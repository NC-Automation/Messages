package com.ncautomation.messages.activities

import android.os.Bundle
import com.ncautomation.commons.activities.BaseSimpleActivity
import com.ncautomation.commons.extensions.beVisibleIf
import com.ncautomation.commons.extensions.getProperPrimaryColor
import com.ncautomation.commons.extensions.underlineText
import com.ncautomation.commons.extensions.updateTextColors
import com.ncautomation.commons.extensions.viewBinding
import com.ncautomation.commons.helpers.APP_ICON_IDS
import com.ncautomation.commons.helpers.APP_LAUNCHER_NAME
import com.ncautomation.commons.helpers.NavigationIcon
import com.ncautomation.commons.helpers.ensureBackgroundThread
import com.ncautomation.commons.interfaces.RefreshRecyclerViewListener
import com.ncautomation.messages.R
import com.ncautomation.messages.databinding.ActivityManageBlockedKeywordsBinding
import com.ncautomation.messages.dialogs.AddBlockedKeywordDialog
import com.ncautomation.messages.dialogs.ManageBlockedKeywordsAdapter
import com.ncautomation.messages.extensions.config
import com.ncautomation.messages.extensions.toArrayList

class ManageBlockedKeywordsActivity : BaseSimpleActivity(), RefreshRecyclerViewListener {
    override fun getAppIconIDs() = intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList()

    override fun getAppLauncherName() = intent.getStringExtra(APP_LAUNCHER_NAME) ?: ""

    private val binding by viewBinding(ActivityManageBlockedKeywordsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateBlockedKeywords()
        setupOptionsMenu()

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.blockKeywordsCoordinator,
            nestedView = binding.manageBlockedKeywordsList,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(scrollingView = binding.manageBlockedKeywordsList, toolbar = binding.blockKeywordsToolbar)
        updateTextColors(binding.manageBlockedKeywordsWrapper)

        binding.manageBlockedKeywordsPlaceholder2.apply {
            underlineText()
            setTextColor(getProperPrimaryColor())
            setOnClickListener {
                addOrEditBlockedKeyword()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.blockKeywordsToolbar, NavigationIcon.Arrow)
    }

    private fun setupOptionsMenu() {
        binding.blockKeywordsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_blocked_keyword -> {
                    addOrEditBlockedKeyword()
                    true
                }

                else -> false
            }
        }
    }

    override fun refreshItems() {
        updateBlockedKeywords()
    }

    private fun updateBlockedKeywords() {
        ensureBackgroundThread {
            val blockedKeywords = config.blockedKeywords
            runOnUiThread {
                ManageBlockedKeywordsAdapter(this, blockedKeywords.toArrayList(), this, binding.manageBlockedKeywordsList) {
                    addOrEditBlockedKeyword(it as String)
                }.apply {
                    binding.manageBlockedKeywordsList.adapter = this
                }

                binding.manageBlockedKeywordsPlaceholder.beVisibleIf(blockedKeywords.isEmpty())
                binding.manageBlockedKeywordsPlaceholder2.beVisibleIf(blockedKeywords.isEmpty())
            }
        }
    }

    private fun addOrEditBlockedKeyword(keyword: String? = null) {
        AddBlockedKeywordDialog(this, keyword) {
            updateBlockedKeywords()
        }
    }
}
