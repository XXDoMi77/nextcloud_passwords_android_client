package com.dominikdomotor.nextcloudpasswords.ui.autofill

import android.*
import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.dominikdomotor.nextcloudpasswords.EncryptedFileManager
//import com.dominikdomotor.nextcloudpasswords.PasswordManager
//import com.dominikdomotor.nextcloudpasswords.SharedPreferencesManager
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.HintWords
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.Password
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import com.google.gson.Gson


class MyAutofillService : AutofillService() {
	
	private var usernameIds: MutableList<AutofillId> = mutableListOf()
	private var passwordIds: MutableList<AutofillId> = mutableListOf()
	private var focusedIds: MutableList<AutofillId> = mutableListOf()
	private var webDomain: String? = null
	
	lateinit var passwords:Array<Password>
	
	override fun onCreate() {
		super.onCreate()
	}
	
	override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback
	) {
		try {
			
			println("\nOnFillRequest called...\n")
			
//			SharedPreferencesManager.init(applicationContext)
//			PasswordManager.init(applicationContext)
			
			val efm = EncryptedFileManager(applicationContext)
			
			passwords = Gson().fromJson(
				efm.read(SPKeys.passwords), Array<Password>::class.java
			).sortedBy { it.label }.toTypedArray()
			
			
			// Get the structure from the request
			val context: List<FillContext> = request.fillContexts
			val structure: AssistStructure = context[context.size - 1].structure
			
			if (structure.activityComponent.packageName == "com.dominikdomotor.nextcloudpasswords") {
				callback.onFailure("...")
				return
			}
			
			val packageManager = applicationContext.packageManager
			val applicationName: String
			
			val applicationInfo = packageManager.getApplicationInfo(structure.activityComponent.packageName, PackageManager.GET_META_DATA)
			applicationName = packageManager.getApplicationLabel(applicationInfo).toString()
			
			for (a in 0 until structure.windowNodeCount) {
				val viewNode = structure.getWindowNodeAt(a).rootViewNode
				traverseNode(viewNode)
			}
			
			data class DataSet(val username: String, val password: String, val label: String)
			
			val dataSets: MutableList<DataSet> = mutableListOf()
			
			
			val webDomainSeparated = webDomain?.split('.')
			webDomain = webDomainSeparated?.get(webDomainSeparated.lastIndex - 1)
			println("webdomain: $webDomain")
			println("applicationName: $applicationName")
			
			if (webDomain?.isNotEmpty() == true) {
				passwords.forEach { password ->
					if (password.label.contains(webDomain!!, true) || password.url.contains(webDomain!!, true)) {
						dataSets.add(DataSet(password.username, password.password, password.label))
					}
				}
			} else {
				if (!(applicationName.isNotEmpty() && applicationName.contains("chrome", true) && webDomain != null)) {
					passwords.forEach { password ->
						if (password.label.contains(applicationName, true) || password.url.contains(applicationName, true)) {
							dataSets.add(DataSet(password.username, password.password, password.label))
						}
					}
				}
			}
			
			val fillResponseBuilder = FillResponse.Builder()
			
			if (dataSets.isNotEmpty()) {
				dataSets.forEach { dataset ->
					val dataSet = Dataset.Builder()
					
					val usernamePresentation = RemoteViews(packageName, R.layout.simple_list_item_1)
					usernamePresentation.setTextViewText(R.id.text1, "\uD83D\uDC64 " + dataset.label + " - " + dataset.username)
					val focusedUsernamePresentation = RemoteViews(packageName, R.layout.simple_list_item_1)
					focusedUsernamePresentation.setTextViewText(R.id.text1, "\uD83D\uDC64❔" + dataset.label + " - " + dataset.username)
					val passwordPresentation = RemoteViews(packageName, R.layout.simple_list_item_1)
					passwordPresentation.setTextViewText(R.id.text1, "\uD83D\uDD11 " + dataset.label + " - " + dataset.username)
					
					if (usernameIds.isNotEmpty() || focusedIds.isNotEmpty()) {
						if (usernameIds.isNotEmpty()) {
							usernameIds.forEach { dataSet.setValue(it, AutofillValue.forText(dataset.username), usernamePresentation) }
						} else  {
							focusedIds.forEach { dataSet.setValue(it, AutofillValue.forText(dataset.username), focusedUsernamePresentation) }
						}
					}
					if (passwordIds.isNotEmpty()) {
						passwordIds.forEach { dataSet.setValue(it, AutofillValue.forText(dataset.password), passwordPresentation) }
					}
					fillResponseBuilder.addDataset(dataSet.build())
//					else {
//						if (focusedIds.isNotEmpty()) {
//
//							focusedIds.forEach { dataSet.setValue(it, AutofillValue.forText(dataset.username), usernamePresentation) }
//
//							val dataSetFocusedUsername = Dataset.Builder()
//							val dataSetFocusedPassword = Dataset.Builder()
//							dataSetFocusedUsername.setValue(focusedId, AutofillValue.forText(dataset.username), usernamePresentation)
//							fillResponseBuilder.addDataset(dataSetFocusedUsername.build())
//							dataSetFocusedPassword.setValue(focusedId, AutofillValue.forText(dataset.password), passwordPresentation)
//							fillResponseBuilder.addDataset(dataSetFocusedPassword.build())
//						}
//					}
					
				}
				val fillResponse = fillResponseBuilder.build()
				dataSets.clear()
				callback.onSuccess(fillResponse)
				println("\n\nSuccess\n\n")
			} else {
			callback.onFailure("Something went wrong when trying to Autofill")
				println("\n\nFail\n\n")
			}
		} catch (e: Exception) {
			callback.onFailure("Something went wrong when trying to Autofill")
			println("\n\nFail\n\n")
			e.printStackTrace()
		}
	}
	
	private var firstFillableFound = false
	private fun traverseNode(viewNode: ViewNode?, viewnodeCeptionDepth: Int = 0) {
		try {
			if (viewNode != null) {
				println("viewnodeCeptionDepth: $viewnodeCeptionDepth")
				if (viewNode.isFocused && viewnodeCeptionDepth > 4) {
					printDetails(viewNode, "Focused")
//						focusedId = viewNode.autofillId!!
					focusedIds += (viewNode.autofillId!!)
//						Toast.makeText(applicationContext, "hint: " + viewNode.hint, Toast.LENGTH_LONG).show()
				}
				if (true
//						((viewNode.inputType.and(InputType.TYPE_CLASS_TEXT) == InputType.TYPE_CLASS_TEXT) ||
//								(viewNode.importantForAutofill != 0) ||
//								(viewNode.autofillType != 0)) &&
//						(viewNode.className?.contains("textview", ignoreCase = true) == false) &&
//						(viewNode.className?.contains("button", ignoreCase = true) == false) &&
//						(viewNode.className?.contains("imageview", ignoreCase = true) == false) &&
//						(viewNode.className?.contains("ProgressBar", ignoreCase = true) == false) &&
//						(viewNode.className?.contains("RecyclerView", ignoreCase = true) == false) //&&
//						(viewNode.className?.contains("ViewGroup", ignoreCase = true) == false) //&&
//						(viewNode.htmlInfo?.attributes?.any { attribute -> attribute.second.contains("button") } == false)
//						&&
//						(viewNode.htmlInfo?.attributes?.any { attribute -> attribute.second.contains("text") } == false) &&
//						(if (viewNode.idPackage != null) viewNode.idPackage?.contains(
//							"chrome", ignoreCase = true
//						) == false else if (viewNode.idEntry != null) viewNode.idEntry?.contains("url_bar", ignoreCase = true) == false else true)
				) {
					if (
							HintWords.usernameHintList.any { hint ->
								viewNode.hint.toString().contains(hint, ignoreCase = true)
									.also { contains -> if (contains) println("\n\nhint: " + viewNode.hint + "\nlistitem: " + hint) }
							}
							||
							HintWords.usernameHintList.any { hint ->
								viewNode.htmlInfo?.attributes?.any { attribute -> attribute.second.contains(hint) } == true
							}
							||
							HintWords.usernameHintList.any { hint ->
								viewNode.autofillHints?.toList()?.any { item -> item.contains(hint) } == true
							}
//							||
//							HintWords.usernameHintList.any { hint ->
//								viewNode.idEntry.toString().contains(hint, ignoreCase = true)
//									.also { contains -> if (contains) println("\n\nhint: " + viewNode.idEntry + "\nlistitem: " + hint) }
//							}
					) {
//						usernameId = viewNode.autofillId!!
						usernameIds += viewNode.autofillId!!
						println("Username hint found: " + viewNode.autofillId)
						println("Hint: " + viewNode.hint.toString())
						printDetails(viewNode, "Username")
					}
					if (
							HintWords.passwordHintList.any { hint ->
								viewNode.hint.toString().contains(hint, ignoreCase = true)
									.also { contains -> if (contains) println("\n\nhint: " + viewNode.hint + "\nlistitem: " + hint) }
							}
							||
							HintWords.passwordHintList.any { hint ->
								viewNode.htmlInfo?.attributes?.any { attribute -> attribute.second.contains(hint) } == true
							}
							||
							HintWords.passwordHintList.any { hint ->
								viewNode.autofillHints?.toList()?.any { item -> item.contains(hint) } == true
							}
//							||
//							HintWords.passwordHintList.any { hint ->
//								viewNode.idEntry.toString().contains(hint, ignoreCase = true)
//									.also { contains -> if (contains) println("\n\nhint: " + viewNode.idEntry + "\nlistitem: " + hint) }
//							}
					) {
//						passwordId = viewNode.autofillId!!
						passwordIds += viewNode.autofillId!!
						println("Password hint found: " + viewNode.autofillId)
						println("Hint: " + viewNode.hint.toString())
						printDetails(viewNode, "Password")
					}
				}
				
			}
			
			if (viewNode != null) {
				if (viewNode.webDomain?.isNotEmpty() == true) {
					webDomain = viewNode.webDomain.toString()
				}
			}
			
			val children: List<ViewNode>? = viewNode?.run {
				(0 until childCount).map { getChildAt(it) }
			}
			
			children?.forEach { childNode: ViewNode ->
				traverseNode(childNode, viewnodeCeptionDepth + 1)
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	override fun onSaveRequest(p0: SaveRequest, p1: SaveCallback) {
		TODO("Not yet implemented")
	}
	
	fun printDetails(viewNode: ViewNode?, description: String) {
		try {
			
			if (viewNode != null) {
				println("\n\n$description\n ------------------------------------------------------------------------------------------------")
				println("autofillId: " + viewNode.autofillId.toString())
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
					println("hintIdEntry: " + viewNode.hintIdEntry.toString())
				}
				println("hint: " + viewNode.hint.toString())
				println("autofillHints: " + (viewNode.autofillHints?.toList()?.forEach { println("autofillhints: $it") }))
				println("extras: " + viewNode.extras.toString())
				println("autofillOptions: " + viewNode.autofillOptions.toString())
				println("autofillType: " + viewNode.autofillType.toString())
				println("autofillValue: " + viewNode.autofillValue.toString())
				println("importantForAutofill: " + viewNode.importantForAutofill.toString())
				println("contentDescription: " + viewNode.contentDescription.toString())
				println("childCount: " + viewNode.childCount.toString())
				println("className: " + viewNode.className.toString())
				println("id: " + viewNode.id.toString())
				println("idEntry: " + viewNode.idEntry.toString())
				println("idPackage: " + viewNode.idPackage.toString())
				println("idType: " + viewNode.idType.toString())
				//Build.VERSION_CODES.R = constant value for 30...
				println("htmlInfo: " + (viewNode.htmlInfo?.attributes?.forEach { println("htmlinfo: $it") }))
				println("inputType: " + viewNode.inputType.toString())
//				println("isAccessibilityFocused: " + viewNode.isAccessibilityFocused.toString())
				println("isActivated: " + viewNode.isActivated.toString())
				println("isAssistBlocked: " + viewNode.isAssistBlocked.toString())
				println("isCheckable: " + viewNode.isCheckable.toString())
				println("isChecked: " + viewNode.isChecked.toString())
				println("isClickable: " + viewNode.isClickable.toString())
				println("isContextClickable: " + viewNode.isContextClickable.toString())
				println("isEnabled: " + viewNode.isEnabled.toString())
//				println("isFocusable: " + viewNode.isFocusable.toString())
				println("isFocused: " + viewNode.isFocused.toString())
//				println("isLongClickable: " + viewNode.isLongClickable.toString())
//				println("isSelected: " + viewNode.isSelected.toString())
//				println("localeList: " + viewNode.localeList.toString())
				println("text: " + viewNode.text.toString())
				println("textIdEntry: " + viewNode.textIdEntry.toString())
//				println("top: " + viewNode.top.toString())
//				println("visibility: " + viewNode.visibility.toString())
//				println("webScheme: " + viewNode.webScheme.toString())
//				println("width: " + viewNode.width.toString())
				println("webDomain: " + viewNode.webDomain.toString())
				print("\n\n")
			}
		} catch (e: Exception) {
			println("Failed to print details")
			e.printStackTrace()
		}
	}
}