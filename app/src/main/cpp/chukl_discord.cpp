// Thin bridge between the Kotlin app and the Discord Social SDK.
// All SDK callbacks run on the thread that calls tick() (the app's main thread),
// so the globals below need no locking.
#include <jni.h>
#include <android/log.h>

#include <chrono>
#include <memory>
#include <string>

#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

#define TAG "ChuklDiscord"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

std::shared_ptr<discordpp::Client> g_client;
uint64_t g_appId = 0;

// SIGNED_OUT, WAITING_LOGIN, CONNECTING, READY, ERROR
std::string g_state = "SIGNED_OUT";
std::string g_message;
std::string g_verifier;
std::string g_pendingTokens;  // "access\nrefresh" handed to Kotlin once

bool g_wantPlaying = false;
bool g_ready = false;
uint64_t g_startMs = 0;

uint64_t nowMs() {
    using namespace std::chrono;
    return (uint64_t)duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count();
}

void setState(const std::string& s, const std::string& msg = "") {
    g_state = s;
    g_message = msg;
    LOGI("state=%s msg=%s", s.c_str(), msg.c_str());
}

void applyPresence() {
    if (!g_client || !g_ready) return;
    if (g_wantPlaying) {
        discordpp::Activity activity;
        activity.SetType(discordpp::ActivityTypes::Playing);
        activity.SetDetails("In Minecraft");
        activity.SetSupportedPlatforms(discordpp::ActivityGamePlatforms::Android);
        discordpp::ActivityTimestamps ts;
        ts.SetStart(g_startMs);
        activity.SetTimestamps(ts);
        g_client->UpdateRichPresence(activity, [](discordpp::ClientResult result) {
            if (result.Successful()) {
                LOGI("presence set");
            } else {
                LOGE("presence failed: %s", result.Error().c_str());
                setState("ERROR", "Could not set status: " + result.Error());
            }
        });
    } else {
        g_client->ClearRichPresence();
        LOGI("presence cleared");
    }
}

void connectWithToken(const std::string& accessToken) {
    setState("CONNECTING");
    g_client->UpdateToken(discordpp::AuthorizationTokenType::Bearer, accessToken,
                          [](discordpp::ClientResult result) {
                              if (!result.Successful()) {
                                  setState("ERROR", "Login failed: " + result.Error());
                                  return;
                              }
                              g_client->Connect();
                          });
}

void onTokens(const discordpp::ClientResult& result, const std::string& access,
              const std::string& refresh) {
    if (!result.Successful()) {
        setState("ERROR", "Login failed: " + result.Error());
        return;
    }
    g_pendingTokens = access + "\n" + refresh;
    connectWithToken(access);
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL Java_com_chukl_discord_Native_init(JNIEnv* env, jclass, jlong appId,
                                                          jstring logDir) {
    if (g_client) return;
    g_appId = (uint64_t)appId;
    g_client = std::make_shared<discordpp::Client>();
    g_client->SetApplicationId(g_appId);

    const char* dir = env->GetStringUTFChars(logDir, nullptr);
    g_client->SetLogDir(dir, discordpp::LoggingSeverity::Info);
    env->ReleaseStringUTFChars(logDir, dir);

    g_client->AddLogCallback(
        [](std::string message, discordpp::LoggingSeverity severity) {
            LOGI("[sdk %s] %s", discordpp::EnumToString(severity), message.c_str());
        },
        discordpp::LoggingSeverity::Info);

    g_client->SetStatusChangedCallback(
        [](discordpp::Client::Status status, discordpp::Client::Error error, int32_t detail) {
            LOGI("sdk status=%s error=%s detail=%d",
                 discordpp::Client::StatusToString(status).c_str(),
                 discordpp::Client::ErrorToString(error).c_str(), detail);
            if (status == discordpp::Client::Status::Ready) {
                g_ready = true;
                setState("READY");
                applyPresence();
            } else {
                g_ready = false;
                if (error != discordpp::Client::Error::None) {
                    setState("ERROR", "Discord connection problem: " +
                                          discordpp::Client::ErrorToString(error));
                } else if (status == discordpp::Client::Status::Connecting ||
                           status == discordpp::Client::Status::Connected ||
                           status == discordpp::Client::Status::Reconnecting) {
                    setState("CONNECTING");
                } else if (status == discordpp::Client::Status::Disconnected &&
                           g_state == "READY") {
                    setState("ERROR", "Connection to Discord was lost. Retrying...");
                }
            }
        });
}

JNIEXPORT void JNICALL Java_com_chukl_discord_Native_tick(JNIEnv*, jclass) {
    discordpp::RunCallbacks();
}

JNIEXPORT void JNICALL Java_com_chukl_discord_Native_startLogin(JNIEnv*, jclass) {
    if (!g_client) return;
    setState("WAITING_LOGIN");
    auto verifier = g_client->CreateAuthorizationCodeVerifier();
    g_verifier = verifier.Verifier();

    discordpp::AuthorizationArgs args;
    args.SetClientId(g_appId);
    args.SetScopes(discordpp::Client::GetDefaultPresenceScopes());
    args.SetCodeChallenge(verifier.Challenge());

    g_client->Authorize(args, [](discordpp::ClientResult result, std::string code,
                                 std::string redirectUri) {
        if (!result.Successful()) {
            setState("ERROR", "Login was cancelled or failed: " + result.Error());
            return;
        }
        setState("CONNECTING");
        g_client->GetToken(g_appId, code, g_verifier, redirectUri,
                           [](discordpp::ClientResult r, std::string access, std::string refresh,
                              discordpp::AuthorizationTokenType, int32_t, std::string) {
                               onTokens(r, access, refresh);
                           });
    });
}

JNIEXPORT void JNICALL Java_com_chukl_discord_Native_loginWithRefresh(JNIEnv* env, jclass,
                                                                      jstring refreshToken) {
    if (!g_client) return;
    const char* t = env->GetStringUTFChars(refreshToken, nullptr);
    std::string refresh(t);
    env->ReleaseStringUTFChars(refreshToken, t);
    setState("CONNECTING");
    g_client->RefreshToken(g_appId, refresh,
                           [](discordpp::ClientResult r, std::string access,
                              std::string newRefresh, discordpp::AuthorizationTokenType, int32_t,
                              std::string) {
                               if (!r.Successful()) {
                                   // Could be a network problem (school wifi): keep the saved
                                   // login and let the app retry. Sign in again if it persists.
                                   setState("ERROR", "Can't reach Discord. Retrying... (" +
                                                         r.Error() + ")");
                                   return;
                               }
                               onTokens(r, access, newRefresh);
                           });
}

JNIEXPORT void JNICALL Java_com_chukl_discord_Native_setPlaying(JNIEnv*, jclass, jboolean on) {
    bool want = on == JNI_TRUE;
    if (want && !g_wantPlaying) g_startMs = nowMs();
    g_wantPlaying = want;
    applyPresence();
}

JNIEXPORT jstring JNICALL Java_com_chukl_discord_Native_getState(JNIEnv* env, jclass) {
    std::string s = g_state + "|" + g_message;
    return env->NewStringUTF(s.c_str());
}

JNIEXPORT jstring JNICALL Java_com_chukl_discord_Native_takeTokens(JNIEnv* env, jclass) {
    std::string s = g_pendingTokens;
    g_pendingTokens.clear();
    return env->NewStringUTF(s.c_str());
}

JNIEXPORT void JNICALL Java_com_chukl_discord_Native_signOut(JNIEnv*, jclass) {
    if (g_client) {
        g_client->ClearRichPresence();
        g_client->Disconnect();
    }
    g_ready = false;
    g_wantPlaying = false;
    setState("SIGNED_OUT");
}

}  // extern "C"
