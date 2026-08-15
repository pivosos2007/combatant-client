/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#include <jni.h>

#include <mutex>
#include <sstream>
#include <string>

#if defined(_WIN32)
#include <windows.h>
#include <aclapi.h>
#elif defined(__linux__)
#include <sys/prctl.h>
#include <sys/resource.h>
#include <cerrno>
#include <cstring>
#endif

namespace {
constexpr jint WINDOWS_PROCESS_DACL = 1;
constexpr jint CORE_DUMPS_DISABLED = 1 << 1;
constexpr jint LINUX_NONDUMPABLE = 1 << 2;

std::once_flag apply_once;
jint applied_mask = 0;
std::string last_error;

void append_error(const std::string& message) {
    if (!last_error.empty()) last_error.append("; ");
    last_error.append(message);
}

#if defined(_WIN32)
std::string windows_error(const char* operation, DWORD error) {
    std::ostringstream message;
    message << operation << " failed with Windows error " << error;
    return message.str();
}

void apply_windows_process_dacl() {
    HANDLE token = nullptr;

    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) {
        append_error(windows_error("OpenProcessToken", GetLastError()));
        return;
    }

    DWORD token_user_size = 0;
    GetTokenInformation(token, TokenUser, nullptr, 0, &token_user_size);

    if (token_user_size == 0) {
        append_error(windows_error("GetTokenInformation(size)", GetLastError()));
        CloseHandle(token);
        return;
    }

    auto* token_user = static_cast<TOKEN_USER*>(LocalAlloc(LPTR, token_user_size));

    if (token_user == nullptr) {
        append_error("LocalAlloc for TokenUser failed");
        CloseHandle(token);
        return;
    }

    if (!GetTokenInformation(token, TokenUser, token_user, token_user_size, &token_user_size)) {
        append_error(windows_error("GetTokenInformation", GetLastError()));
        LocalFree(token_user);
        CloseHandle(token);
        return;
    }

    BYTE system_sid_buffer[SECURITY_MAX_SID_SIZE];
    BYTE administrators_sid_buffer[SECURITY_MAX_SID_SIZE];
    DWORD system_sid_size = sizeof(system_sid_buffer);
    DWORD administrators_sid_size = sizeof(administrators_sid_buffer);

    if (!CreateWellKnownSid(WinLocalSystemSid, nullptr, system_sid_buffer, &system_sid_size)
            || !CreateWellKnownSid(WinBuiltinAdministratorsSid, nullptr,
                    administrators_sid_buffer, &administrators_sid_size)) {
        append_error(windows_error("CreateWellKnownSid", GetLastError()));
        LocalFree(token_user);
        CloseHandle(token);
        return;
    }

    constexpr DWORD dangerous_access = PROCESS_CREATE_THREAD
            | PROCESS_VM_OPERATION
            | PROCESS_VM_READ
            | PROCESS_VM_WRITE
            | PROCESS_DUP_HANDLE
            | PROCESS_CREATE_PROCESS
            | PROCESS_SET_QUOTA
            | PROCESS_SET_INFORMATION
            | PROCESS_SUSPEND_RESUME
            | WRITE_DAC
            | WRITE_OWNER;
    EXPLICIT_ACCESSW entries[4]{};

    entries[0].grfAccessPermissions = dangerous_access;
    entries[0].grfAccessMode = DENY_ACCESS;
    entries[0].grfInheritance = NO_INHERITANCE;
    entries[0].Trustee.TrusteeForm = TRUSTEE_IS_SID;
    entries[0].Trustee.TrusteeType = TRUSTEE_IS_USER;
    entries[0].Trustee.ptstrName = static_cast<LPWSTR>(token_user->User.Sid);

    entries[1].grfAccessPermissions = PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE;
    entries[1].grfAccessMode = GRANT_ACCESS;
    entries[1].grfInheritance = NO_INHERITANCE;
    entries[1].Trustee.TrusteeForm = TRUSTEE_IS_SID;
    entries[1].Trustee.TrusteeType = TRUSTEE_IS_USER;
    entries[1].Trustee.ptstrName = static_cast<LPWSTR>(token_user->User.Sid);

    entries[2].grfAccessPermissions = PROCESS_ALL_ACCESS;
    entries[2].grfAccessMode = GRANT_ACCESS;
    entries[2].grfInheritance = NO_INHERITANCE;
    entries[2].Trustee.TrusteeForm = TRUSTEE_IS_SID;
    entries[2].Trustee.TrusteeType = TRUSTEE_IS_WELL_KNOWN_GROUP;
    entries[2].Trustee.ptstrName = reinterpret_cast<LPWSTR>(system_sid_buffer);

    entries[3].grfAccessPermissions = PROCESS_ALL_ACCESS;
    entries[3].grfAccessMode = GRANT_ACCESS;
    entries[3].grfInheritance = NO_INHERITANCE;
    entries[3].Trustee.TrusteeForm = TRUSTEE_IS_SID;
    entries[3].Trustee.TrusteeType = TRUSTEE_IS_GROUP;
    entries[3].Trustee.ptstrName = reinterpret_cast<LPWSTR>(administrators_sid_buffer);

    PACL dacl = nullptr;
    DWORD result = SetEntriesInAclW(4, entries, nullptr, &dacl);

    if (result == ERROR_SUCCESS) {
        result = SetSecurityInfo(
                GetCurrentProcess(),
                SE_KERNEL_OBJECT,
                DACL_SECURITY_INFORMATION | PROTECTED_DACL_SECURITY_INFORMATION,
                nullptr,
                nullptr,
                dacl,
                nullptr
        );
    }

    if (result == ERROR_SUCCESS) {
        applied_mask |= WINDOWS_PROCESS_DACL;
    } else {
        append_error(windows_error("SetSecurityInfo", result));
    }

    if (dacl != nullptr) LocalFree(dacl);
    LocalFree(token_user);
    CloseHandle(token);
}
#elif defined(__linux__)
void apply_linux_hardening() {
    rlimit core_limit{};

    if (setrlimit(RLIMIT_CORE, &core_limit) == 0) {
        applied_mask |= CORE_DUMPS_DISABLED;
    } else {
        append_error(std::string("setrlimit(RLIMIT_CORE) failed: ") + std::strerror(errno));
    }

    if (prctl(PR_SET_DUMPABLE, 0, 0, 0, 0) == 0) {
        applied_mask |= LINUX_NONDUMPABLE;
    } else {
        append_error(std::string("prctl(PR_SET_DUMPABLE) failed: ") + std::strerror(errno));
    }
}
#endif

void apply_hardening() {
    std::call_once(apply_once, [] {
#if defined(_WIN32)
        apply_windows_process_dacl();
#elif defined(__linux__)
        apply_linux_hardening();
#else
        append_error("unsupported operating system");
#endif
    });
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    apply_hardening();
    return JNI_VERSION_1_8;
}

extern "C" JNIEXPORT jint JNICALL
Java_combatant_client_runtime_nativeguard_NativeMemoryGuard_nativeApply(JNIEnv*, jclass) {
    apply_hardening();
    return applied_mask;
}

extern "C" JNIEXPORT jstring JNICALL
Java_combatant_client_runtime_nativeguard_NativeMemoryGuard_nativeLastError(JNIEnv* env, jclass) {
    return env->NewStringUTF(last_error.c_str());
}
