package com.trispeech.app.models

import com.trispeech.app.transcription.ModelTier

object ModelCatalog {
    private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"
    private val sha256 = mapOf(
        ModelTier.MAXIMUM_SPEED to "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
        ModelTier.BEST_BALANCE to "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
        ModelTier.MAXIMUM_QUALITY to "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b"
    )

    fun spec(tier: ModelTier) = ModelDownloadSpec(
        tier = tier,
        url = "$BASE_URL/${tier.fileName}?download=true",
        sha256 = requireNotNull(sha256[tier])
    )
}
