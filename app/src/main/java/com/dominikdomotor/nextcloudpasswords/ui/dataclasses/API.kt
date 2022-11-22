package com.dominikdomotor.nextcloudpasswords.ui.dataclasses

object API {
	const val passwordsPath: String = "/index.php/apps/passwords"
	
	object Session{
		object Request {
			const val url: String = "$passwordsPath/api/1.0/session/request"
			const val method: String = "GET"
			val headers: List<Pair<String, String>> = listOf(Pair("Connection","keep-alive"))
			const val doOutput: Boolean = false
		}
		object Open{
			const val url: String = "$passwordsPath/api/1.0/session/open"
			const val method: String = "POST"
			val headers: List<Pair<String, String>> = listOf(Pair("Content-Type", "application/json"), Pair("Connection","keep-alive"))
			const val doOutput: Boolean = true
		}
		object Close{
			const val url: String = "$passwordsPath/api/1.0/session/close"
			const val method: String = "GET"
			val headers: List<Pair<String, String>> = listOf(Pair("Connection","keep-alive"))
			const val doOutput: Boolean = false
		}
		object Keepalive{
			const val url: String = "$passwordsPath/api/1.0/session/keepalive"
			const val method: String = "GET"
			val headers: List<Pair<String, String>> = listOf(Pair("Connection","keep-alive"))
			const val doOutput: Boolean = false
		}
	}
	
	object Password{
		object List{
			const val url: String = "$passwordsPath/api/1.0/password/list"
			const val method: String = "GET"
			val headers: kotlin.collections.List<Pair<String, String>> = listOf(Pair("Connection","keep-alive"))
			const val doOutput: Boolean = false
		}
		object ListDetails{
			const val url: String = "$passwordsPath/api/1.0/password/list"
			const val method: String = "POST"
			val headers: kotlin.collections.List<Pair<String, String>> = listOf(Pair("Content-Type", "application/json"), Pair("Connection","keep-alive"))
			const val doOutput: Boolean = true
		}
		object Create{
			const val url: String = "$passwordsPath/api/1.0/password/create"
			const val method: String = "POST"
			val headers: kotlin.collections.List<Pair<String, String>> = listOf(Pair("Content-Type", "application/json"), Pair("Connection","keep-alive"))
			const val doOutput: Boolean = true
		}
		object Update{
			const val url: String = "$passwordsPath/api/1.0/password/update"
			const val method: String = "PATCH"
			val headers: kotlin.collections.List<Pair<String, String>> = listOf(Pair("Content-Type", "application/json"), Pair("Connection","keep-alive"))
			const val doOutput: Boolean = true
		}
		object Delete{
			const val url: String = "$passwordsPath/api/1.0/password/delete"
			const val method: String = "DEL"
			val headers: kotlin.collections.List<Pair<String, String>> = listOf(Pair("Content-Type", "application/json"), Pair("Connection","keep-alive"))
			const val doOutput: Boolean = true
			
		}
	}
	
	
}