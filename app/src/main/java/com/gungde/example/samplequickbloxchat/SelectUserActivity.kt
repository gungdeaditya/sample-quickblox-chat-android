package com.gungde.example.samplequickbloxchat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import com.gungde.example.samplequickbloxchat.adapter.CheckboxUsersAdapter
import com.quickblox.chat.model.QBChatDialog
import com.quickblox.core.QBEntityCallback
import com.quickblox.core.exception.QBResponseException
import com.quickblox.sample.core.utils.Toaster
import com.quickblox.users.QBUsers
import com.quickblox.users.model.QBUser
import java.util.concurrent.TimeUnit

import kotlinx.android.synthetic.main.activity_select_user.*

class SelectUserActivity : BaseActivity() {

    private val EXTRA_QB_DIALOG = "qb_dialog"
    val EXTRA_QB_USERS = "qb_users"
    val MINIMUM_CHAT_OCCUPANTS_SIZE = 2
    private val CLICK_DELAY = TimeUnit.SECONDS.toMillis(2)

    private var usersAdapter: CheckboxUsersAdapter? = null
    private var lastClickTime = 0L

    fun start(context: Context) {
        val intent = Intent(context, SelectUserActivity::class.java)
        context.startActivity(intent)
    }

    fun startForResult(activity: Activity, code: Int) {
        startForResult(activity, code, null)
    }

    fun startForResult(activity: Activity, code: Int, dialog: QBChatDialog?) {
        val intent = Intent(activity, SelectUserActivity::class.java)
        intent.putExtra(EXTRA_QB_DIALOG, dialog)
        activity.startActivityForResult(intent, code)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_user)

        val listHeader = LayoutInflater.from(this)
                .inflate(R.layout.include_list_hint_header, list_select_users, false) as TextView
        listHeader.setText(R.string.select_users_list_hint)
        list_select_users.addHeaderView(listHeader, null, false)

        if (isEditingChat()) {
            setActionBarTitle(R.string.select_users_edit_chat)
        } else {
            setActionBarTitle(R.string.select_users_create_chat)
        }
        actionBar.setDisplayHomeAsUpEnabled(true)

        loadUsersFromQb()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_select_users, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (SystemClock.uptimeMillis() - lastClickTime < CLICK_DELAY) {
            return super.onOptionsItemSelected(item)
        }
        lastClickTime = SystemClock.uptimeMillis()

        when (item.itemId) {
            R.id.menu_select_people_action_done -> {
                if (usersAdapter != null) {
                    val users = usersAdapter?.getSelectedUsers()
                    if (users!!.size >= MINIMUM_CHAT_OCCUPANTS_SIZE) {
                        passResultToCallerActivity()
                    } else {
                        Toaster.shortToast(R.string.select_users_choose_users)
                    }
                }
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun getSnackbarAnchorView(): View {
        return findViewById(R.id.layout_root)
    }

    private fun passResultToCallerActivity() {
        val result = Intent()
        val selectedUsers = ArrayList(usersAdapter?.getSelectedUsers())
        result.putExtra(EXTRA_QB_USERS, selectedUsers)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun loadUsersFromQb() {
        val tags = java.util.ArrayList<String>()
        tags.add(App.getSampleConfigs().users_tag)

        progress_select_users.setVisibility(View.VISIBLE)
        QBUsers.getUsers(null).performAsync(object : QBEntityCallback<ArrayList<QBUser>> {
            override fun onSuccess(result: ArrayList<QBUser>, params: Bundle) {
                val dialog = intent.getSerializableExtra(EXTRA_QB_DIALOG) as QBChatDialog?
                usersAdapter = CheckboxUsersAdapter(this@SelectUserActivity, result)
                if(dialog!=null){
                    usersAdapter?.addSelectedUsers(dialog?.occupants)
                }

                list_select_users.setAdapter(usersAdapter)
                progress_select_users.setVisibility(View.GONE)
            }

            override fun onError(e: QBResponseException) {
                showErrorSnackbar(R.string.select_users_get_users_error, e,
                        View.OnClickListener { loadUsersFromQb() })
                progress_select_users.setVisibility(View.GONE)
            }
        })
    }

    private fun isEditingChat(): Boolean {
        return intent.getSerializableExtra(EXTRA_QB_DIALOG) != null
    }
}
