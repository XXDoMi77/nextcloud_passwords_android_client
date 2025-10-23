package com.dominikdomotor.nextcloudpasswords.managers // package com.dominikdomotor.nextcloudpasswords
//
// import android.content.Context
// import com.dominikdomotor.nextcloudpasswords.dataclasses.Password
// import com.dominikdomotor.nextcloudpasswords.dataclasses.SPKeys
// import com.google.gson.Gson
// import com.ionspin.kotlin.crypto.box.crypto_box_SEEDBYTES
// import com.ionspin.kotlin.crypto.generichash.GenericHash
// import com.ionspin.kotlin.crypto.generichash.crypto_generichash_blake2b_KEYBYTES_MAX
// import com.ionspin.kotlin.crypto.generichash.crypto_generichash_blake2b_SALTBYTES
// import com.ionspin.kotlin.crypto.pwhash.PasswordHash
// import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_MEMLIMIT_INTERACTIVE
// import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_OPSLIMIT_SENSITIVE
// import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_argon2id_ALG_ARGON2ID13
// import com.ionspin.kotlin.crypto.util.LibsodiumRandom
// import com.ionspin.kotlin.crypto.util.encodeToUByteArray
//

object EncryptionManager {}

// object EncryptionManager {
//
//	fun init() {}
//
//	// Extension functions to convert hexadecimal strings to byte arrays and vice versa
//	@OptIn(ExperimentalUnsignedTypes::class) fun String.decodeHex(): UByteArray {
//		return this.chunked(2).map { it.toInt(16).toUByte() }.toUByteArray()
//	}
//
//	@OptIn(ExperimentalUnsignedTypes::class) fun UByteArray.encodeToHex(): String {
//		return this.joinToString("") { it.toString(16).padStart(2, '0') }
//	}
//
//	@OptIn(ExperimentalUnsignedTypes::class) fun solveChallenge(masterPassword: String, salts: List<String>): String {
//
//		// Decode salts
//		val passwordSalt = salts[0].decodeHex()
//		val genericHashKey = salts[1].decodeHex()
//		val passwordHashSalt = salts[2].decodeHex()
//
//		// Compute generic hash
//		val genericHash = GenericHash.genericHash(message = (masterPassword.encodeToUByteArray() +
// passwordSalt).toUByteArray(), key = genericHashKey.toUByteArray())
//
//		// Compute secret using crypto_pwhash
//		val secret = PasswordHash.pwhash(
//			outputLength = crypto_box_SEEDBYTES,
//			password = genericHash.toByteArray().toString(),
//			salt = passwordHashSalt.toUByteArray(),
//			opsLimit = crypto_pwhash_OPSLIMIT_SENSITIVE,
//			memLimit = crypto_pwhash_MEMLIMIT_INTERACTIVE,
//			algorithm = crypto_pwhash_argon2id_ALG_ARGON2ID13
//		)
//
//		return secret.toUByteArray().encodeToHex()
//	}
//
//	@OptIn(ExperimentalUnsignedTypes::class) fun createChallenge(masterPassword: String): List<String> {
//
//		// Generate random salts
////		GenericHash.genericHashKeygen()
//		val passwordSalt = LibsodiumRandom.buf(256)
//		val genericHashKey = LibsodiumRandom.buf(crypto_generichash_blake2b_KEYBYTES_MAX)
//		val passwordHashSalt = LibsodiumRandom.buf(crypto_generichash_blake2b_SALTBYTES)
//
//		// Compute generic hash
//		val genericHash = GenericHash.genericHash(
//			message = masterPassword.encodeToUByteArray() + passwordSalt,
//			key = genericHashKey
//		)
//
//		// Compute secret using crypto_pwhash
//		val secret = PasswordHash.pwhash(
//			outputLength = crypto_box_SEEDBYTES,
//			password = genericHash.toString(),
//			salt = passwordHashSalt,
//			opsLimit = crypto_pwhash_OPSLIMIT_SENSITIVE,
//			memLimit = crypto_pwhash_MEMLIMIT_INTERACTIVE,
//			algorithm = crypto_pwhash_argon2id_ALG_ARGON2ID13
//		)
//
//		// Create challenge response object
//
//		return listOf(
//			passwordSalt.encodeToHex(),
//			genericHashKey.encodeToHex(),
//			passwordHashSalt.encodeToHex()
//		)
//	}
// }
