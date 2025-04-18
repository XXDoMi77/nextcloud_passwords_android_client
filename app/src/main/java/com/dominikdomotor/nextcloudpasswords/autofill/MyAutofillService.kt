package com.dominikdomotor.nextcloudpasswords.autofill

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
import com.dominikdomotor.nextcloudpasswords.GF
import com.dominikdomotor.nextcloudpasswords.managers.EFM
import com.dominikdomotor.nextcloudpasswords.managers.NM
import com.dominikdomotor.nextcloudpasswords.managers.SM


class MyAutofillService : AutofillService() {

    private var usernameIds: MutableList<AutofillId> = mutableListOf()
    private var passwordIds: MutableList<AutofillId> = mutableListOf()
    private var focusedIds: MutableList<AutofillId> = mutableListOf()
    private var webDomain: String? = null

    override fun onCreate() {
        super.onCreate()
        EFM.init(this)
        SM.init()
        NM.init(this)
    }

    override fun onFillRequest(
        request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback
    ) {
        try {

            GF.println("\nOnFillRequest called...\n")

            // Get the structure from the request
            val context: List<FillContext> = request.fillContexts
            val structure: AssistStructure = context[context.size - 1].structure

            if (structure.activityComponent.packageName == "com.dominikdomotor.nextcloudpasswords") {
                callback.onFailure("...")
                return
            }

            val packageManager = applicationContext.packageManager
            val applicationName: String

            val applicationInfo = packageManager.getApplicationInfo(
                structure.activityComponent.packageName,
                PackageManager.GET_META_DATA
            )
            applicationName = packageManager.getApplicationLabel(applicationInfo).toString()

            for (a in 0 until structure.windowNodeCount) {
                val viewNode = structure.getWindowNodeAt(a).rootViewNode
                traverseNode(viewNode)
            }

            data class DataSet(val username: String, val password: String, val label: String)

            val dataSets: MutableList<DataSet> = mutableListOf()


            val webDomainSeparated = webDomain?.split('.')
            webDomain = webDomainSeparated?.get(webDomainSeparated.lastIndex - 1)
            GF.println("webdomain: $webDomain")
            GF.println("applicationName: $applicationName")

            if (webDomain?.isNotEmpty() == true) {
                SM.getPasswords().forEach { password ->
                    if (password.label.contains(webDomain!!, true) || password.url.contains(
                            webDomain!!,
                            true
                        )
                    ) {
                        dataSets.add(DataSet(password.username, password.password, password.label))
                    }
                }
            } else {
                if (!(applicationName.isNotEmpty() && applicationName.contains(
                        "chrome",
                        true
                    ) && webDomain != null)
                ) {
                    SM.getPasswords().forEach { password ->
                        if (password.label.contains(applicationName, true) || password.url.contains(
                                applicationName,
                                true
                            )
                        ) {
                            dataSets.add(
                                DataSet(
                                    password.username,
                                    password.password,
                                    password.label
                                )
                            )
                        }
                    }
                }
            }

            val fillResponseBuilder = FillResponse.Builder()

            if (usernameIds.isNotEmpty() && passwordIds.isNotEmpty()) {
                usernameIds.forEach { usernameId ->
                    dataSets.forEach { dataSet ->
                        val unknownUsernamePresentation =
                            RemoteViews(packageName, R.layout.simple_list_item_1)
                        unknownUsernamePresentation.setTextViewText(
                            R.id.text1,
                            "\uD83D\uDC64❔" + dataSet.label + " - " + dataSet.username
                        )
                        val unknownPasswordPresentation =
                            RemoteViews(packageName, R.layout.simple_list_item_1)
                        unknownPasswordPresentation.setTextViewText(
                            R.id.text1,
                            "\uD83D\uDD11❔" + dataSet.label + " - " + dataSet.username
                        )
                        val usernamePresentation =
                            RemoteViews(packageName, R.layout.simple_list_item_1)
                        usernamePresentation.setTextViewText(
                            R.id.text1,
                            "\uD83D\uDC64 " + dataSet.label + " - " + dataSet.username
                        )

                        val finishedDataSet = Dataset.Builder()
                        finishedDataSet.setValue(
                            usernameId,
                            AutofillValue.forText(dataSet.username),
                            usernamePresentation
                        )
                        passwordIds.forEach { passwordId ->
                            finishedDataSet.setValue(
                                passwordId,
                                AutofillValue.forText(dataSet.password)
                            )
                        }
                        fillResponseBuilder.addDataset(finishedDataSet.build())

                        fillResponseBuilder.addDataset(
                            Dataset.Builder()
                                .setValue(
                                    usernameId,
                                    AutofillValue.forText(dataSet.username),
                                    unknownUsernamePresentation
                                )
                                .build()
                        )
                        fillResponseBuilder.addDataset(
                            Dataset.Builder()
                                .setValue(
                                    usernameId,
                                    AutofillValue.forText(dataSet.password),
                                    unknownPasswordPresentation
                                )
                                .build()
                        )
                    }
                }
                passwordIds.forEach { passwordId ->
                    dataSets.forEach { dataSet ->
                        val unknownUsernamePresentation =
                            RemoteViews(packageName, R.layout.simple_list_item_1)
                        unknownUsernamePresentation.setTextViewText(
                            R.id.text1,
                            "\uD83D\uDC64❔" + dataSet.label + " - " + dataSet.username
                        )
                        val unknownPasswordPresentation =
                            RemoteViews(packageName, R.layout.simple_list_item_1)
                        unknownPasswordPresentation.setTextViewText(
                            R.id.text1,
                            "\uD83D\uDD11❔" + dataSet.label + " - " + dataSet.username
                        )
                        val passwordPresentation =
                            RemoteViews(packageName, R.layout.simple_list_item_1)
                        passwordPresentation.setTextViewText(
                            R.id.text1,
                            "\uD83D\uDD11 " + dataSet.label + " - " + dataSet.username
                        )

                        val finishedDataSet = Dataset.Builder()
                        finishedDataSet.setValue(
                            passwordId,
                            AutofillValue.forText(dataSet.password),
                            passwordPresentation
                        )
                        usernameIds.forEach { usernameId ->
                            finishedDataSet.setValue(
                                usernameId,
                                AutofillValue.forText(dataSet.username)
                            )
                        }
                        fillResponseBuilder.addDataset(finishedDataSet.build())

                        fillResponseBuilder.addDataset(
                            Dataset.Builder()
                                .setValue(
                                    passwordId,
                                    AutofillValue.forText(dataSet.username),
                                    unknownUsernamePresentation
                                )
                                .build()
                        )
                        fillResponseBuilder.addDataset(
                            Dataset.Builder()
                                .setValue(
                                    passwordId,
                                    AutofillValue.forText(dataSet.password),
                                    unknownPasswordPresentation
                                )
                                .build()
                        )
                    }
                }

                val fillResponse = fillResponseBuilder.build()
                dataSets.clear()
                callback.onSuccess(fillResponse)
                GF.println("\n\nSuccess\n\n")
            } else {
                callback.onFailure("Something went wrong when trying to Autofill")
                GF.println("\n\nFail\n\n")
            }

//			if (dataSets.isNotEmpty()) {
//				dataSets.forEach { dataset ->
//					val dataSet = Dataset.Builder()
//
//					val usernamePresentation = RemoteViews(packageName, R.layout.simple_list_item_1)
//					usernamePresentation.setTextViewText(R.id.text1, "\uD83D\uDC64 " + dataset.label + " - " + dataset.username)
//					val focusedUsernamePresentation = RemoteViews(packageName, R.layout.simple_list_item_1)
//					focusedUsernamePresentation.setTextViewText(R.id.text1, "\uD83D\uDC64❔" + dataset.label + " - " + dataset.username)
//					val passwordPresentation = RemoteViews(packageName, R.layout.simple_list_item_1)
//					passwordPresentation.setTextViewText(R.id.text1, "\uD83D\uDD11 " + dataset.label + " - " + dataset.username)
//
//
//					if (usernameIds.isNotEmpty() || ) {
//						if (usernameIds.isNotEmpty()) {
//							usernameIds.forEach {
//								dataSet.setValue(it, AutofillValue.forText(dataset.username), usernamePresentation) }
//
//							usernameIds.forEach {
//								dataSet.setValue(it, AutofillValue.forText(dataset.username)) }
//						} else  {
//							focusedIds.forEach { dataSet.setValue(it, AutofillValue.forText(dataset.username), focusedUsernamePresentation) }
//						}
//					}
//					if (passwordIds.isNotEmpty()) {
//						passwordIds.forEach { dataSet.setValue(it, AutofillValue.forText(dataset.password), passwordPresentation) }
//					}
//
//
//
//					fillResponseBuilder.addDataset(dataSet.build())
////					else {
////						if (focusedIds.isNotEmpty()) {
////
////							focusedIds.forEach { dataSet.setValue(it, AutofillValue.forText(dataset.username), usernamePresentation) }
////
////							val dataSetFocusedUsername = Dataset.Builder()
////							val dataSetFocusedPassword = Dataset.Builder()
////							dataSetFocusedUsername.setValue(focusedId, AutofillValue.forText(dataset.username), usernamePresentation)
////							fillResponseBuilder.addDataset(dataSetFocusedUsername.build())
////							dataSetFocusedPassword.setValue(focusedId, AutofillValue.forText(dataset.password), passwordPresentation)
////							fillResponseBuilder.addDataset(dataSetFocusedPassword.build())
////						}
////					}
//
//				}
//				val fillResponse = fillResponseBuilder.build()
//				dataSets.clear()
//				callback.onSuccess(fillResponse)
//				GF.println("\n\nSuccess\n\n")
//			} else {
//			callback.onFailure("Something went wrong when trying to Autofill")
//				GF.println("\n\nFail\n\n")
//			}


        } catch (e: Exception) {
            callback.onFailure("Something went wrong when trying to Autofill")
            GF.println("\n\nFail\n\n")
            e.printStackTrace()
        }
    }

    //    private var firstFillableFound = false
    private fun traverseNode(viewNode: ViewNode?, viewnodeCeptionDepth: Int = 0) {
        try {
            if (viewNode != null) {
//                GF.println("viewnodeCeptionDepth: $viewnodeCeptionDepth")
                if (viewNode.isFocused && viewnodeCeptionDepth > 4) {
                    printDetails(viewNode, "Focused")
//						focusedId = viewNode.autofillId!!
                    focusedIds += (viewNode.autofillId!!)
//						Toast.makeText(applicationContext, "hint: " + viewNode.hint, Toast.LENGTH_LONG).show()
                }
                var isUsernameHintInViewNode = false

                HintWords.usernameHintList.forEach { hint ->
                    // Check if the hint exists in viewNode's hint property
                    if (viewNode.hint.toString().contains(hint, ignoreCase = true)) {
                        GF.println("\n\nHint found in viewNode.hint: ${viewNode.hint}\nMatched hint from list: $hint")
                        isUsernameHintInViewNode = true
                    }

                    // Check if the hint exists in viewNode's idEntry property
                    if (viewNode.idEntry.toString().contains(hint, ignoreCase = true)) {
                        GF.println("\n\nHint found in viewNode.idEntry: ${viewNode.idEntry}\nMatched list item: $hint")
                        isUsernameHintInViewNode = true
                    }

                    // Check if the hint exists in viewNode's HTML attributes
                    viewNode.htmlInfo?.attributes?.forEach { attribute ->
                        if (attribute.toString().contains(hint, ignoreCase = true)) {
                            GF.println("\n\nHint found in viewNode.htmlInfo.attributes: ${attribute}\nMatched list item: $hint")
                            isUsernameHintInViewNode = true
                        }
                    }

                    // Check if the hint exists in viewNode's HTML attributes
                    viewNode.autofillHints?.forEach { autofillHint ->
                        if (autofillHint.toString().contains(hint, ignoreCase = true)) {
                            GF.println("\n\nHint found in viewNode.autofillHints: ${autofillHint}\nMatched list item: $hint")
                            isUsernameHintInViewNode = true
                        }
                    }
                }

                if (isUsernameHintInViewNode) {
                    //usernameId = viewNode.autofillId!!
                    usernameIds += viewNode.autofillId!!
                    GF.println("Username hint found: " + viewNode.autofillId)
                    GF.println("Hint: " + viewNode.hint.toString())
                    printDetails(viewNode, "Username")
                }

                var isPasswordHintInViewNode = false

                HintWords.passwordHintList.forEach { hint ->
                    // Check if the hint exists in viewNode's hint property
                    if (viewNode.hint.toString().contains(hint, ignoreCase = true)) {
                        GF.println("\n\nHint found in viewNode.hint: ${viewNode.hint}\nMatched hint from list: $hint")
                        isPasswordHintInViewNode = true
                    }

                    // Check if the hint exists in viewNode's idEntry property
                    if (viewNode.idEntry.toString().contains(hint, ignoreCase = true)) {
                        GF.println("\n\nHint found in viewNode.idEntry: ${viewNode.idEntry}\nMatched list item: $hint")
                        isPasswordHintInViewNode = true
                    }

                    // Check if the hint exists in viewNode's HTML attributes
                    viewNode.htmlInfo?.attributes?.forEach { attribute ->
                        if (attribute.toString().contains(hint, ignoreCase = true)) {
                            GF.println("\n\nHint found in viewNode.htmlInfo.attributes: ${attribute}\nMatched list item: $hint")
                            isPasswordHintInViewNode = true
                        }
                    }

                    // Check if the hint exists in viewNode's HTML attributes
                    viewNode.autofillHints?.forEach { autofillHint ->
                        if (autofillHint.toString().contains(hint, ignoreCase = true)) {
                            GF.println("\n\nHint found in viewNode.autofillHints: ${autofillHint}\nMatched list item: $hint")
                            isPasswordHintInViewNode = true
                        }
                    }
                }

                if (isPasswordHintInViewNode) {
//						passwordId = viewNode.autofillId!!
                    passwordIds += viewNode.autofillId!!
                    GF.println("Password hint found: " + viewNode.autofillId)
                    GF.println("Hint: " + viewNode.hint.toString())
                    printDetails(viewNode, "Password")
                }


                if (viewNode.webDomain?.isNotEmpty() == true) {
                    webDomain = viewNode.webDomain.toString()
                }

                val children: List<ViewNode> = viewNode.run {
                    (0 until childCount).map { getChildAt(it) }
                }

                children.forEach { childNode: ViewNode ->
                    traverseNode(childNode, viewnodeCeptionDepth + 1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSaveRequest(p0: SaveRequest, p1: SaveCallback) {
        TODO("Not yet implemented")
    }

    private fun printDetails(viewNode: ViewNode?, description: String) {
        try {

            if (viewNode != null) {
                GF.println("\n\n$description\n ------------------------------------------------------------------------------------------------")
                GF.println("autofillId: " + viewNode.autofillId.toString())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    GF.println("hintIdEntry: " + viewNode.hintIdEntry.toString())
                }
                GF.println("hint: " + viewNode.hint.toString())
                GF.println(
                    "autofillHints: " + (viewNode.autofillHints?.toList()
                        ?.forEach { GF.println("autofillhints: $it") })
                )
                GF.println("extras: " + viewNode.extras.toString())
                GF.println("autofillOptions: " + viewNode.autofillOptions.toString())
                GF.println("autofillType: " + viewNode.autofillType.toString())
                GF.println("autofillValue: " + viewNode.autofillValue.toString())
                GF.println("importantForAutofill: " + viewNode.importantForAutofill.toString())
                GF.println("contentDescription: " + viewNode.contentDescription.toString())
                GF.println("childCount: " + viewNode.childCount.toString())
                GF.println("className: " + viewNode.className.toString())
                GF.println("id: " + viewNode.id.toString())
                GF.println("idEntry: " + viewNode.idEntry.toString())
                GF.println("idPackage: " + viewNode.idPackage.toString())
                GF.println("idType: " + viewNode.idType.toString())
                //Build.VERSION_CODES.R = constant value for 30...
                GF.println("htmlInfo: " + (viewNode.htmlInfo?.attributes?.forEach {
                    GF.println(
                        "htmlinfo: $it"
                    )
                }))
                GF.println("inputType: " + viewNode.inputType.toString())
//				GF.prtln("isAccessibilityFocused: " + viewNode.isAccessibilityFocused.toString())
                GF.println("isActivated: " + viewNode.isActivated.toString())
                GF.println("isAssistBlocked: " + viewNode.isAssistBlocked.toString())
                GF.println("isCheckable: " + viewNode.isCheckable.toString())
                GF.println("isChecked: " + viewNode.isChecked.toString())
                GF.println("isClickable: " + viewNode.isClickable.toString())
                GF.println("isContextClickable: " + viewNode.isContextClickable.toString())
                GF.println("isEnabled: " + viewNode.isEnabled.toString())
//				GF.prtln("isFocusable: " + viewNode.isFocusable.toString())
                GF.println("isFocused: " + viewNode.isFocused.toString())
//				GF.prtln("isLongClickable: " + viewNode.isLongClickable.toString())
//				GF.prtln("isSelected: " + viewNode.isSelected.toString())
//				GF.prtln("localeList: " + viewNode.localeList.toString())
                GF.println("text: " + viewNode.text.toString())
                GF.println("textIdEntry: " + viewNode.textIdEntry.toString())
//				GF.prtln("top: " + viewNode.top.toString())
//				GF.prtln("visibility: " + viewNode.visibility.toString())
//				GF.prtln("webScheme: " + viewNode.webScheme.toString())
//				GF.prtln("width: " + viewNode.width.toString())
                GF.println("webDomain: " + viewNode.webDomain.toString())
                print("\n\n")
            }
        } catch (e: Exception) {
            GF.println("Failed to print details")
            e.printStackTrace()
        }
    }
}