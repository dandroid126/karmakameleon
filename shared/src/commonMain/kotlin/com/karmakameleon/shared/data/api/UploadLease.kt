package com.karmakameleon.shared.data.api

/** Where to upload a file for Reddit: post the [fields], in order, then the file, to [uploadUrl]. */
data class UploadLease(
    val uploadUrl: String,
    val fields: List<Pair<String, String>>,
    val assetId: String,
)
