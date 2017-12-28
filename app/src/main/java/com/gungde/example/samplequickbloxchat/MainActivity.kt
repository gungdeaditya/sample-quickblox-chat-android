package com.gungde.example.samplequickbloxchat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.view.ActionMode
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import com.gungde.example.samplequickbloxchat.adapter.DialogsAdapter
import com.gungde.example.samplequickbloxchat.managers.DialogsManager
import com.gungde.example.samplequickbloxchat.utils.chat.ChatHelper
import com.gungde.example.samplequickbloxchat.utils.qb.QbChatDialogMessageListenerImp
import com.gungde.example.samplequickbloxchat.utils.qb.QbDialogHolder
import com.gungde.example.samplequickbloxchat.utils.qb.callback.QbEntityCallbackImpl
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.quickblox.chat.QBChatService
import com.quickblox.chat.QBIncomingMessagesManager
import com.quickblox.chat.QBSystemMessagesManager
import com.quickblox.chat.exception.QBChatException
import com.quickblox.chat.listeners.QBChatDialogMessageListener
import com.quickblox.chat.listeners.QBSystemMessageListener
import com.quickblox.chat.model.QBChatDialog
import com.quickblox.chat.model.QBChatMessage
import com.quickblox.core.QBEntityCallback
import com.quickblox.core.exception.QBResponseException
import com.quickblox.core.request.QBRequestGetBuilder
import com.quickblox.messages.services.SubscribeService
import com.quickblox.sample.core.gcm.GooglePlayServicesHelper
import com.quickblox.sample.core.ui.dialog.ProgressDialogFragment
import com.quickblox.sample.core.utils.SharedPrefsHelper
import com.quickblox.sample.core.utils.constant.GcmConsts
import com.quickblox.users.model.QBUser

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : BaseActivity(),DialogsManager.ManagingDialogsCallbacks  {

    private val TAG = MainActivity::class.java!!.getSimpleName()
    private val REQUEST_SELECT_PEOPLE = 174
    private val REQUEST_DIALOG_ID_FOR_UPDATE = 165

    private var requestBuilder: QBRequestGetBuilder? = null
    private var dialogsAdapter: DialogsAdapter? = null
    private var currentActionMode: ActionMode? = null
    private var menu: Menu? = null
    private var skipRecords = 0
    private var isProcessingResultInProgress: Boolean = false

    private var googlePlayServicesHelper: GooglePlayServicesHelper? = null
    private var pushBroadcastReceiver: BroadcastReceiver? = null
    private var allDialogsMessagesListener: QBChatDialogMessageListener? = null
    private var systemMessagesListener: SystemMessagesListener? = null
    private var systemMessagesManager: QBSystemMessagesManager? = null
    private var incomingMessagesManager: QBIncomingMessagesManager? = null
    private var dialogsManager: DialogsManager? = null
    private var currentUser: QBUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        googlePlayServicesHelper = GooglePlayServicesHelper()

        pushBroadcastReceiver = PushBroadcastReceiver()

        allDialogsMessagesListener = AllDialogsMessageListener()
        systemMessagesListener = SystemMessagesListener()

        dialogsManager = DialogsManager()

        currentUser = ChatHelper.getCurrentUser()

        initUi()

        setActionBarTitle(getString(R.string.dialogs_logged_in_as, currentUser?.getFullName()))

        registerQbChatListeners()
        if (QbDialogHolder.getInstance().getDialogs().size > 0) {
            loadDialogsFromQb(true, true)
        } else {
            loadDialogsFromQb(false, true)
        }
    }

    fun start(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        context.startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        googlePlayServicesHelper?.checkPlayServicesAvailable(this)

        LocalBroadcastManager.getInstance(this).registerReceiver(pushBroadcastReceiver,
                IntentFilter(GcmConsts.ACTION_NEW_GCM_EVENT))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pushBroadcastReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterQbChatListeners()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_dialogs, menu)
        this.menu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (isProcessingResultInProgress) {
            return super.onOptionsItemSelected(item)
        }

        when (item.itemId) {
            R.id.menu_dialogs_action_logout -> {
                userLogout()
                item.isEnabled = false
                invalidateOptionsMenu()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            isProcessingResultInProgress = true
            if (requestCode == REQUEST_SELECT_PEOPLE) {
                val selectedUsers = data!!
                        .getSerializableExtra(SelectUserActivity().EXTRA_QB_USERS) as java.util.ArrayList<QBUser>

                if (isPrivateDialogExist(selectedUsers)) {
                    selectedUsers.remove(ChatHelper.getCurrentUser())
                    val existingPrivateDialog = QbDialogHolder.getInstance().getPrivateDialogWithUser(selectedUsers[0])
                    isProcessingResultInProgress = false
                    ChatActivity().startForResult(this@MainActivity, REQUEST_DIALOG_ID_FOR_UPDATE, existingPrivateDialog)
                } else {
                    ProgressDialogFragment.show(supportFragmentManager, R.string.create_chat)
                    createDialog(selectedUsers)
                }
            } else if (requestCode == REQUEST_DIALOG_ID_FOR_UPDATE) {
                if (data != null) {
                    val dialogId = data.getStringExtra(ChatActivity().EXTRA_DIALOG_ID)
                    loadUpdatedDialog(dialogId)
                } else {
                    isProcessingResultInProgress = false
                    updateDialogsList()
                }
            }
        } else {
            updateDialogsAdapter()
        }
    }

    private fun isPrivateDialogExist(allSelectedUsers: java.util.ArrayList<QBUser>): Boolean {
        val selectedUsers = java.util.ArrayList<QBUser>()
        selectedUsers.addAll(allSelectedUsers)
        selectedUsers.remove(ChatHelper.getCurrentUser())
        return selectedUsers.size == 1 && QbDialogHolder.getInstance().hasPrivateDialogWithUser(selectedUsers[0])
    }

    private fun loadUpdatedDialog(dialogId: String) {
        ChatHelper.getInstance().getDialogById(dialogId, object : QbEntityCallbackImpl<QBChatDialog>() {
            override fun onSuccess(result: QBChatDialog, bundle: Bundle) {
                isProcessingResultInProgress = false
                QbDialogHolder.getInstance().addDialog(result)
                updateDialogsAdapter()
            }

            override fun onError(e: QBResponseException) {
                isProcessingResultInProgress = false
            }
        })
    }

    override fun getSnackbarAnchorView(): View {
        return findViewById(R.id.layout_root)
    }

    override fun startSupportActionMode(callback: ActionMode.Callback): ActionMode {
        currentActionMode = super.startSupportActionMode(callback)
        return currentActionMode!!
    }

    private fun userLogout() {
        ChatHelper.getInstance().destroy()
        SubscribeService.unSubscribeFromPushes(this@MainActivity)
        SharedPrefsHelper.getInstance().removeQbUser()
        LoginActivity().start(this@MainActivity)
        QbDialogHolder.getInstance().clear()
        ProgressDialogFragment.hide(supportFragmentManager)
        finish()
    }

    private fun updateDialogsList() {
        skipRecords = 0
        requestBuilder?.setSkip(skipRecords)
        loadDialogsFromQb(true, true)
    }

    fun onStartNewChatClick(view: View) {
        SelectUserActivity().startForResult(this, REQUEST_SELECT_PEOPLE)
    }

    private fun initUi() {
        dialogsAdapter = DialogsAdapter(this, ArrayList(QbDialogHolder.getInstance().getDialogs().values))
        val listHeader = LayoutInflater.from(this)
                .inflate(R.layout.include_list_hint_header, list_dialogs_chats, false) as TextView
        listHeader.setText(R.string.dialogs_list_hint)
        list_dialogs_chats.setEmptyView(layout_chat_empty)
        list_dialogs_chats.addHeaderView(listHeader, null, false)
        list_dialogs_chats.setAdapter(dialogsAdapter)
        list_dialogs_chats.setOnItemClickListener{ parent, view, position, id ->
            val selectedDialog = parent.getItemAtPosition(position) as QBChatDialog
            if (currentActionMode == null) {
                ChatActivity().startForResult(this@MainActivity, REQUEST_DIALOG_ID_FOR_UPDATE, selectedDialog)
            } else {
                dialogsAdapter?.toggleSelection(selectedDialog)
            }
        }
        list_dialogs_chats.setOnItemLongClickListener(AdapterView.OnItemLongClickListener { parent, view, position, id ->
            val selectedDialog = parent.getItemAtPosition(position) as QBChatDialog
            startSupportActionMode(DeleteActionModeCallback())
            dialogsAdapter?.selectItem(selectedDialog)
            true
        })
        requestBuilder = QBRequestGetBuilder()

        swipy_refresh_layout.setOnRefreshListener(SwipyRefreshLayout.OnRefreshListener {
            skipRecords += ChatHelper.DIALOG_ITEMS_PER_PAGE
            requestBuilder?.setSkip(skipRecords)
            loadDialogsFromQb(true, false)
        })
    }

    private fun registerQbChatListeners() {
        incomingMessagesManager = QBChatService.getInstance().incomingMessagesManager
        systemMessagesManager = QBChatService.getInstance().systemMessagesManager

        if (incomingMessagesManager != null) {
            incomingMessagesManager?.addDialogMessageListener(if (allDialogsMessagesListener != null)
                allDialogsMessagesListener
            else
                AllDialogsMessageListener())
        }

        if (systemMessagesManager != null) {
            systemMessagesManager?.addSystemMessageListener(if (systemMessagesListener != null)
                systemMessagesListener
            else
                SystemMessagesListener())
        }

        dialogsManager?.addManagingDialogsCallbackListener(this)
    }

    private fun unregisterQbChatListeners() {
        if (incomingMessagesManager != null) {
            incomingMessagesManager?.removeDialogMessageListrener(allDialogsMessagesListener)
        }

        if (systemMessagesManager != null) {
            systemMessagesManager?.removeSystemMessageListener(systemMessagesListener)
        }

        dialogsManager?.removeManagingDialogsCallbackListener(this)
    }

    private fun createDialog(selectedUsers: java.util.ArrayList<QBUser>) {
        ChatHelper.getInstance().createDialogWithSelectedUsers(selectedUsers,
                object : QBEntityCallback<QBChatDialog> {
                    override fun onSuccess(dialog: QBChatDialog, args: Bundle) {
                        isProcessingResultInProgress = false
                        dialogsManager?.sendSystemMessageAboutCreatingDialog(systemMessagesManager, dialog)
                        ChatActivity().startForResult(this@MainActivity, REQUEST_DIALOG_ID_FOR_UPDATE, dialog)
                        ProgressDialogFragment.hide(supportFragmentManager)
                    }

                    override fun onError(e: QBResponseException) {
                        isProcessingResultInProgress = false
                        ProgressDialogFragment.hide(supportFragmentManager)
                        showErrorSnackbar(R.string.dialogs_creation_error, null, null)
                    }
                }
        )
    }

    private fun loadDialogsFromQb(silentUpdate: Boolean, clearDialogHolder: Boolean) {
        isProcessingResultInProgress = true
        if (!silentUpdate) {
            progress_dialogs.setVisibility(View.VISIBLE)
        }

        ChatHelper.getInstance().getDialogs(requestBuilder, object : QBEntityCallback<java.util.ArrayList<QBChatDialog>> {
            override fun onSuccess(dialogs: java.util.ArrayList<QBChatDialog>, bundle: Bundle) {
                isProcessingResultInProgress = false
                progress_dialogs.setVisibility(View.GONE)
                swipy_refresh_layout.setRefreshing(false)

                if (clearDialogHolder) {
                    QbDialogHolder.getInstance().clear()
                }
                QbDialogHolder.getInstance().addDialogs(dialogs)
                updateDialogsAdapter()
            }

            override fun onError(e: QBResponseException) {
                isProcessingResultInProgress = false
                progress_dialogs.setVisibility(View.GONE)
                swipy_refresh_layout.setRefreshing(false)
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateDialogsAdapter() {
        dialogsAdapter?.updateList(ArrayList(QbDialogHolder.getInstance().getDialogs().values))
    }

    override fun onDialogCreated(chatDialog: QBChatDialog) {
        updateDialogsAdapter()
    }

    override fun onDialogUpdated(chatDialog: String) {
        updateDialogsAdapter()
    }

    override fun onNewDialogLoaded(chatDialog: QBChatDialog) {
        updateDialogsAdapter()
    }

    private inner class DeleteActionModeCallback : ActionMode.Callback {
        init {
            fab_dialogs_new_chat.hide()
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.action_mode_dialogs, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.menu_dialogs_action_delete -> {
                    deleteSelectedDialogs()
                    if (currentActionMode != null) {
                        currentActionMode?.finish()
                    }
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            currentActionMode = null
            dialogsAdapter?.clearSelection()
            fab_dialogs_new_chat.show()
        }

        private fun deleteSelectedDialogs() {
            val selectedDialogs = dialogsAdapter?.getSelectedItems()
            ChatHelper.getInstance().deleteDialogs(selectedDialogs, object : QBEntityCallback<java.util.ArrayList<String>> {
                override fun onSuccess(dialogsIds: java.util.ArrayList<String>, bundle: Bundle) {
                    QbDialogHolder.getInstance().deleteDialogs(dialogsIds)
                    updateDialogsAdapter()
                }

                override fun onError(e: QBResponseException) {
                    showErrorSnackbar(R.string.dialogs_deletion_error, e,
                            View.OnClickListener { deleteSelectedDialogs() })
                }
            })
        }
    }

    private inner class PushBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Get extra data included in the Intent
            val message = intent.getStringExtra(GcmConsts.EXTRA_GCM_MESSAGE)
            Log.v(TAG, "Received broadcast " + intent.action + " with data: " + message)
            skipRecords = 0
            requestBuilder?.setSkip(skipRecords)
            loadDialogsFromQb(true, true)
        }
    }

    private inner class SystemMessagesListener : QBSystemMessageListener {
        override fun processMessage(qbChatMessage: QBChatMessage) {
            dialogsManager?.onSystemMessageReceived(qbChatMessage)
        }

        override fun processError(e: QBChatException, qbChatMessage: QBChatMessage) {

        }
    }

    private inner class AllDialogsMessageListener : QbChatDialogMessageListenerImp() {
        override fun processMessage(dialogId: String, qbChatMessage: QBChatMessage, senderId: Int?) {
            if (senderId != ChatHelper.getCurrentUser().getId()) {
                dialogsManager?.onGlobalMessageReceived(dialogId, qbChatMessage)
            }
        }
    }
}
