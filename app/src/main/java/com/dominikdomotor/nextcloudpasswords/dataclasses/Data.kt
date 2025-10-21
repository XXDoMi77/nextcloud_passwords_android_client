package com.dominikdomotor.nextcloudpasswords.dataclasses

import android.graphics.Bitmap
import com.dominikdomotor.nextcloudpasswords.dataclasses.passwords.Passwords
import com.dominikdomotor.nextcloudpasswords.dataclasses.shares.Shares

data class Data(
	var settings:Settings = Settings(),
	var passwords: Passwords = Passwords(),
	var partners: MutableMap<String, String> = mutableMapOf(),
	var shares: Shares = Shares()
)
