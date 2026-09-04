package li.gkd.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import li.gkd.db.CategoryConfig
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.edit
import li.gkd.db.Db
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.ui.share.Loadable
import li.gkd.app.util.EnableGroupOption
import li.gkd.app.util.findOption

data class SubsCategoryUiState(
    val subscription: RawSubscription,
    val categoryConfigMap: Loadable<Map<Int, CategoryConfig>>,
)

class SubsCategoryVm(val route: SubsCategoryRoute) : BaseViewModel() {
    val showAddCategoryDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private val subscription = requiredSubscription(route.subsItemId)
    private val categoryConfigsFlow = Db.categoryConfigDao.queryConfig(route.subsItemId)

    val uiState = subscription.buildUiState(
        initialValue = { rawSubscription ->
            buildUiState(rawSubscription, Loadable.Loading)
        },
    ) { rawSubscription ->
        categoryConfigsFlow.map { configs ->
            buildUiState(
                rawSubscription = rawSubscription,
                categoryConfigMap = Loadable.Ready(
                    configs.associateBy { it.categoryKey },
                ),
            )
        }
    }

    private fun buildUiState(
        rawSubscription: RawSubscription,
        categoryConfigMap: Loadable<Map<Int, CategoryConfig>>,
    ) = SubsCategoryUiState(
        subscription = rawSubscription,
        categoryConfigMap = categoryConfigMap,
    )

    fun setAddCategoryDialogVisible(visible: Boolean) {
        showAddCategoryDialogFlow.value = visible
    }

    suspend fun setCategoryEnabled(
        category: RawSubscription.RawCategory,
        enabled: Boolean?,
    ): String {
        val option = EnableGroupOption.objects.findOption(enabled)
        val state = uiState.value.value ?: error("The subscription hasn't loaded yet")
        val rawSubscription = subscription.requireValue()
        val categoryConfigMap = state.categoryConfigMap.value
            ?: error("The category config hasn't loaded yet")
        val oldConfig = categoryConfigMap[category.key]
        Db.categoryConfigDao.insert(
            (oldConfig ?: CategoryConfig(
                enable = option.value,
                subsId = rawSubscription.id,
                categoryKey = category.key,
            )).copy(enable = option.value),
        )
        return option.label
    }

    suspend fun addCategory(name: String, description: String): String {
        subscription.update { current ->
            if (current.categories.any { category -> category.name == name }) {
                error("Cannot add a category with the same name")
            }
            current.edit {
                putCategory(
                    RawSubscription.RawCategory(
                        key = (current.categories.maxOfOrNull { it.key } ?: -1) + 1,
                        enable = null,
                        name = name,
                        desc = description,
                    ),
                )
            }
        }
        return "Added successfully"
    }
}
