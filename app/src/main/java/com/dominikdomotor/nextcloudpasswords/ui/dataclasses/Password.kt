package com.dominikdomotor.nextcloudpasswords.ui.dataclasses

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap

data class Password(
	var id: String = "",
	var created: Long = 0,
	var updated: Long = 0,
	var edited: Long = 0,
	var share: String = "",
	var shared: Boolean = false,
	var revision: String = "",
	var label: String = "",
	var username: String = "",
	var password: String = "",
	var notes: String = "",
	var customFields: String = "",
	var url: String = "",
	var status: Int = 0,
	var statusCode: String = "",
	var hash: String = "",
	var folder: String = "",
	var cseKey: String = "",
	var cseType: String = "",
	var sseType: String = "",
	var hidden: Boolean = false,
	var trashed: Boolean = false,
	var favorite: Boolean = false,
	var editable: Boolean = false,
	var client: String = "",
	var favicon: ByteArray = byteArrayOf()
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		
		other as Password
		
		if (!favicon.contentEquals(other.favicon)) return false
		
		return true
	}
	
	override fun hashCode(): Int {
		return favicon.contentHashCode()
	}
}