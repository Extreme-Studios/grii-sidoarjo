package com.grii.sidoarjo.data.model

data class SongRegistrationPayload(
    val title: String,
    val synopsis: String,
    val year: String,
    val kategori: String,
    val uploadLink: String,
    val copyrightAgreement: Boolean,
    val coverImageBase64: String? = null,
    val coverImageFileName: String? = null,
    val coverImageMimeType: String? = null
)
