package com.dominikdomotor.nextcloudpasswords.ui.autofill

import android.R
import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.dominikdomotor.nextcloudpasswords.App
import com.dominikdomotor.nextcloudpasswords.passwords
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.Password
import com.dominikdomotor.nextcloudpasswords.ui.dataclasses.SPKeys
import com.google.gson.Gson


class MyAutofillService : AutofillService() {
	
	lateinit var usernameId: AutofillId
	lateinit var passwordId: AutofillId
	
	
	private val allViewNodes = mutableListOf<ViewNode>()
	private var webViewUrl: String = String()
	
//	private var allViewNodes: MutableList<ViewNode> = mutableListOf()
	
	override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback
	) {
		
		if (App.sharedPreferencesAreInitialized()){
			passwords = Gson().fromJson(App.sharedPreferences().getString(SPKeys.passwords, SPKeys.not_found), Array<Password>::class.java).sortedBy { it.label }.toTypedArray()
		}
		
		println("onFillRequest called")
		
		// Get the structure from the request
		val context: List<FillContext> = request.fillContexts
		val structure: AssistStructure = context[context.size - 1].structure
		
		println("package name: " + structure.activityComponent.packageName)
		
		if (structure.activityComponent.packageName == "com.dominikdomotor.nextcloudpasswords"){
			callback.onFailure("...")
			return
		}
		
		val splitPackageName = structure.activityComponent.packageName.split(".")
		
		val pm = applicationContext.packageManager
		val ai: ApplicationInfo? = try {
			pm.getApplicationInfo(structure.activityComponent.packageName, 0)
		} catch (e: PackageManager.NameNotFoundException) {
			null
		}
		val applicationName = (if (ai != null) pm.getApplicationLabel(ai) else "(unknown)") as String
		
		println("appname: $applicationName")
		
		
		//viewNode.autofillType != View.AUTOFILL_TYPE_NONE
		
		var usernameFill: String = "username"
		var passwordFill: String = "password"
		var labelFill: String = "label"
		
		passwords.forEach { password ->
			if (password.label.contains(applicationName, true) || password.url.contains(applicationName, true) && applicationName != "com" && applicationName != "android") {
				usernameFill = password.username
				passwordFill = password.password
				labelFill = password.label
			}
		}
		
		for (a in 0 until structure.windowNodeCount) {
			val viewNode = structure.getWindowNodeAt(a).rootViewNode
			traverseNode(viewNode)
			println("url?: " + viewNode?.webDomain)
		}
		
		var firstEditTextFound: Boolean = false
		if (applicationName.contains("chrome", true)) {
			passwords.forEach { password ->
				if (password.label.contains(webViewUrl, true) || password.url.contains(webViewUrl, true)){
					usernameFill = password.username
					passwordFill = password.password
					labelFill = password.label
				}
			}
			allViewNodes.forEach {
				if (!firstEditTextFound && it.autofillType == 1) {
					usernameId = it.autofillId!!
					it.autofillHints?.forEach { println("autofillHints: $it") }
					println("autofillHints : " + it.autofillHints)
					println("autofillId : " + it.autofillId)
					println("autofillOptions : " + it.autofillOptions)
					println("autofillType : " + it.autofillType)
					println("autofillValue : " + it.autofillValue)
					println("childCount : " + it.childCount)
					println("className : " + it.className)
					println("contentDescription : " + it.contentDescription)
					println("extras : " + it.extras)
					println("hint : " + it.hint)
					println("hintIdEntry : " + it.hintIdEntry)
					println("htmlInfo : " + it.htmlInfo)
					println("id : " + it.id)
					println("idEntry : " + it.idEntry)
					println("idPackage : " + it.idPackage)
					println("idType : " + it.idType)
					println("importantForAutofill : " + it.importantForAutofill)
					println("inputType : " + it.inputType)
					println("isAccessibilityFocused : " + it.isAccessibilityFocused)
					println("isActivated : " + it.isActivated)
					println("isFocusable : " + it.isFocusable)
					println("isFocused : " + it.isFocused)
					println("isSelected : " + it.isSelected)
					println("receiveContentMimeTypes : " + it.receiveContentMimeTypes)
					println("text : " + it.text)
					println("textIdEntry : " + it.textIdEntry)
					println("webDomain : " + it.webDomain)
					println("webScheme : " + it.webScheme)
					firstEditTextFound = true
					return@forEach
				}
				if (firstEditTextFound && it.autofillType == 1) {
					passwordId = it.autofillId!!
					println("asdasd2" + it.className)
				}
			}
		} else {
			allViewNodes.forEach {
				if (!firstEditTextFound && (it.hint == View.AUTOFILL_HINT_USERNAME || it.hint == View.AUTOFILL_HINT_EMAIL_ADDRESS || it.hint.toString().contains("mail", true) || it.hint.toString().contains("user", true) || it.hint.toString().contains("phone", true) || it.hint.toString().contains("phone", true) || it.isFocused || it.className?.contains("EditText") == true || it.className?.contains("AutoCompleteTextView") == true)) {
					usernameId = it.autofillId!!
					println("asdasd1" + it.className)
					firstEditTextFound = true
					return@forEach
				}
				if (firstEditTextFound && (it.hint.toString().contains("password", true) || it.className?.contains("EditText") == true)) {
					passwordId = it.autofillId!!
					println("asdasd2" + it.className)
				}
			}
		}
		
		// Build the presentation of the datasets
		val usernamePresentation = RemoteViews(packageName, R.layout.simple_list_item_1)
		usernamePresentation.setTextViewText(android.R.id.text1, labelFill)
		val passwordPresentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
		passwordPresentation.setTextViewText(android.R.id.text1, labelFill)
		
		// Add a dataset to the response
		val fillResponse: FillResponse.Builder = FillResponse.Builder()
		val datasetBuilder: Dataset.Builder = Dataset.Builder()
		
		if (this::usernameId.isInitialized) {
			println("userId\t: $usernameId")
			datasetBuilder.setValue(usernameId, AutofillValue.forText(usernameFill), usernamePresentation)
		}
		if (this::passwordId.isInitialized) {
			println("pwId\t: $passwordId")
			datasetBuilder.setValue(passwordId, AutofillValue.forText(passwordFill), passwordPresentation)
		}
		if (datasetBuilder != Dataset.Builder()) {
			println("FillResponse builded")
			fillResponse.addDataset(datasetBuilder.build())
			callback.onSuccess(fillResponse.build())
		} else {
			println("No matching password found")
			callback.onFailure("No matching password found")
		}

//		val autofillIds: Array<AutofillId> = arrayOf()
//		fillAbleViewNodes.forEachIndexed { index, it ->
//			autofillIds[index] = it.autofillId!!
//		}
	}
	
	
	
	
	private fun traverseNode(viewNode: ViewNode?) {
//		println("been called")

//		WebView(applicationContext).url
		
		if (viewNode != null) {
			if(viewNode.webDomain?.isNotEmpty() == true){
				println("url?: " + viewNode.webDomain.toString())
				webViewUrl = viewNode.webDomain.toString()
			}
		}
		

		if (viewNode?.className?.contains("WebView", true) == true){
//			println("url?: " + viewNode.webDomain.toString())
//			println("----------------------------------------------------------------------------------------")
//			viewNode.autofillHints?.forEach { println("autofillHints: $it") }
//			println("autofillId : " + viewNode?.autofillId)
//			println("autofillOptions : " + viewNode?.autofillOptions)
//			println("autofillType : " + viewNode?.autofillType)
//			println("autofillValue : " + viewNode?.autofillValue)
//			println("childCount : " + viewNode?.childCount)
//			println("className : " + viewNode?.className)
//			println("contentDescription : " + viewNode?.contentDescription)
//			println("extras : " + viewNode?.extras)
//			println("hint : " + viewNode?.hint)
//			println("hintIdEntry : " + viewNode?.hintIdEntry)
//			println("htmlInfo : " + viewNode?.htmlInfo)
//			println("id : " + viewNode?.id)
//			println("idEntry : " + viewNode?.idEntry)
//			println("idPackage : " + viewNode?.idPackage)
//			println("idType : " + viewNode?.idType)
//			println("importantForAutofill : " + viewNode?.importantForAutofill)
//			println("inputType : " + viewNode?.inputType)
//			println("isAccessibilityFocused : " + viewNode?.isAccessibilityFocused)
//			println("isActivated : " + viewNode?.isActivated)
//			println("isFocusable : " + viewNode?.isFocusable)
//			println("isFocused : " + viewNode?.isFocused)
//			println("isSelected : " + viewNode?.isSelected)
//			println("receiveContentMimeTypes : " + viewNode?.receiveContentMimeTypes)
//			println("text : " + viewNode?.text)
//			println("textIdEntry : " + viewNode?.textIdEntry)
//			println("webDomain : " + viewNode?.webDomain)
//			println("webScheme : " + viewNode?.webScheme)
		}
		
		
		try {
			if (viewNode != null) {
				allViewNodes.add(viewNode)
//				println("ViewNode added")
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
		if (viewNode?.autofillHints?.isNotEmpty() == true) {
			// If the client app provides autofill hints, you can obtain them using:
			// viewNode.getAutofillHints();
		} else {
			// Or use your own heuristics to describe the contents of a view
			// using methods such as getText() or getHint().
		}
		
		val children: List<ViewNode>? = viewNode?.run {
			(0 until childCount).map { getChildAt(it) }
		}
		
		children?.forEach { childNode: ViewNode ->
			traverseNode(childNode)
		}
	}
	
	override fun onSaveRequest(p0: SaveRequest, p1: SaveCallback) {
		TODO("Not yet implemented")
	}
}