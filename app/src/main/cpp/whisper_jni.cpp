#include <jni.h>
#include <string>
#ifdef TRISPEECH_HAS_WHISPER
#include "whisper.h"
#include <fstream>
#include <sstream>
#include <vector>
#endif

extern "C" JNIEXPORT jboolean JNICALL
Java_com_trispeech_app_nativebridge_WhisperJniBridge_nativeCoreAvailable(JNIEnv *, jobject) {
#ifdef TRISPEECH_HAS_WHISPER
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_trispeech_app_nativebridge_WhisperJniBridge_nativeTranscribe(
    JNIEnv *env, jobject, jstring modelPath, jstring wavPath, jstring language) {
#ifndef TRISPEECH_HAS_WHISPER
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "whisper.cpp sources are not bundled");
    return nullptr;
#else
    // whisper.cpp consumes float PCM. Decode canonical 16-bit mono WAV after the 44-byte header.
    const char *model = env->GetStringUTFChars(modelPath, nullptr);
    const char *wav = env->GetStringUTFChars(wavPath, nullptr);
    const char *lang = env->GetStringUTFChars(language, nullptr);
    std::ifstream input(wav, std::ios::binary);
    if (!input) { env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Unable to open WAV file"); return nullptr; }
    input.seekg(0, std::ios::end); const auto bytes = input.tellg();
    if (bytes <= 44) { env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "WAV file has no PCM audio"); return nullptr; }
    input.seekg(44, std::ios::beg);
    std::vector<int16_t> pcm((static_cast<size_t>(bytes) - 44) / 2); input.read(reinterpret_cast<char*>(pcm.data()), static_cast<std::streamsize>(pcm.size() * 2));
    std::vector<float> samples(pcm.size()); for (size_t i = 0; i < pcm.size(); ++i) samples[i] = pcm[i] / 32768.0f;
    auto *ctx = whisper_init_from_file_with_params(model, whisper_context_default_params());
    if (!ctx) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Unable to load Whisper model"); return nullptr; }
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY); params.print_progress = false; params.language = std::string(lang) == "auto" ? nullptr : lang;
    if (whisper_full(ctx, params, samples.data(), static_cast<int>(samples.size())) != 0) { whisper_free(ctx); env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Whisper inference failed"); return nullptr; }
    auto escape = [](const std::string &value) { std::string out; for (char c : value) { if (c == '\"' || c == '\\') out += '\\'; if (c == '\n') out += "\\n"; else if (c != '\r') out += c; } return out; };
    std::string text; std::ostringstream segments; segments << "[";
    const int count = whisper_full_n_segments(ctx);
    for (int i = 0; i < count; ++i) {
        const std::string part = whisper_full_get_segment_text(ctx, i); text += part;
        if (i) segments << ",";
        segments << "{\"start_ms\":" << whisper_full_get_segment_t0(ctx, i) * 10
                 << ",\"end_ms\":" << whisper_full_get_segment_t1(ctx, i) * 10
                 << ",\"text\":\"" << escape(part) << "\"}";
    }
    segments << "]";
    const char *detected = whisper_lang_str(whisper_full_lang_id(ctx));
    std::string json = "{\"text\":\"" + escape(text) + "\",\"language\":\"" + (detected ? detected : "auto") + "\",\"segments\":" + segments.str() + "}";
    whisper_free(ctx); env->ReleaseStringUTFChars(modelPath, model); env->ReleaseStringUTFChars(wavPath, wav); env->ReleaseStringUTFChars(language, lang);
    return env->NewStringUTF(json.c_str());
#endif
}
