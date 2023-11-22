package com.dominikdomotor.nextcloudpasswords.ui.dataclasses

object API {
	const val PASSWORDS_PATH = "/index.php/apps/passwords"
	
	object Session {
		object Request {
			const val URL = "$PASSWORDS_PATH/api/1.0/session/request"
			const val METHOD = "GET"
			val HEADERS = listOf("Connection" to "keep-alive")
			const val DO_OUTPUT = false
		}
		
		object Open {
			const val URL = "$PASSWORDS_PATH/api/1.0/session/open"
			const val METHOD = "POST"
			val HEADERS = listOf("Content-Type" to "application/json", "Connection" to "keep-alive")
			const val DO_OUTPUT = true
		}
		
		object Close {
			const val URL = "$PASSWORDS_PATH/api/1.0/session/close"
			const val METHOD = "GET"
			val HEADERS = listOf("Connection" to "keep-alive")
			const val DO_OUTPUT = false
		}
		
		object Keepalive {
			const val URL = "$PASSWORDS_PATH/api/1.0/session/keepalive"
			const val METHOD = "GET"
			val HEADERS = listOf("Connection" to "keep-alive")
			const val DO_OUTPUT = false
		}
	}
	
	object Password {
//		object ListDetails {
//			const val URL = "$PASSWORDS_PATH/api/1.0/password/list"
//			val HEADERS = listOf(
//				"Authorization" to basicAuth,
//				"Content-Type" to "application/json",
//				"Connection" to "keep-alive"
//			)
//			const val METHOD = "POST"
//			const val OUTPUT = "{\n" + "    \"details\": \"model+revisions+folder+tags+shares\"\n" + "}"
//		}
		object List {
			const val URL = "$PASSWORDS_PATH/api/1.0/password/list"
			val HEADERS = listOf(
				"Connection" to "keep-alive"
			)
			const val METHOD = "GET"
			const val DO_OUTPUT = false
		}
		
		object Create {
			const val URL = "$PASSWORDS_PATH/api/1.0/password/create"
			const val METHOD = "POST"
			val HEADERS = listOf(
				"Content-Type" to "application/json",
				"Connection" to "keep-alive"
			)
			const val DO_OUTPUT = true
		}
		
		object Update {
			const val URL = "$PASSWORDS_PATH/api/1.0/password/update"
			const val METHOD = "PATCH"
			val HEADERS = listOf(
				"Content-Type" to "application/json",
				"Connection" to "keep-alive"
			)
			const val DO_OUTPUT = true
		}
		
		object Delete {
			const val URL = "$PASSWORDS_PATH/api/1.0/password/delete"
			const val METHOD = "DELETE"
			val HEADERS = listOf(
				"Content-Type" to "application/json",
				"Connection" to "keep-alive"
			)
			const val DO_OUTPUT = true
		}
	}
	
	object Share {
		object Partners {
			const val URL = "$PASSWORDS_PATH/api/1.0/share/partners"
			const val METHOD = "GET"
			val HEADERS = listOf(
				"Content-Type" to "application/json",
				"Connection" to "keep-alive"
			)
			const val DO_OUTPUT = true
			const val BODY = "{\n" +
					"    \"limit\" : 256\n" +
					"}"
		}
	}
}
