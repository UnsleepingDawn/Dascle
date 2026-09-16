package com.fitplan.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.repository.BodyMetricRepository
import com.fitplan.domain.repository.UserProfileRepository
import com.fitplan.ui.profile.ProfileFormState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 首次打开 App 时的个人信息引导。
 *
 * 这里只管「走没走过引导」：没走过时 `MainActivity` 不进 `HomeScreen`，所以
 * 漏练弹窗之类的启动打扰也不会抢在引导前面。填了体重 / 体脂就顺手记成今天的数据，
 * 用户在统计页立刻能看到东西。
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class OnboardingScreenModel(
    private val userProfileRepository: UserProfileRepository,
    private val bodyMetricRepository: BodyMetricRepository,
) : ViewModel() {

    /** null 表示还没读出来；偏好读取本身是同步的，实际只在第一帧之前的一瞬间。 */
    private val _onboarded = MutableStateFlow<Boolean?>(null)
    val onboarded: StateFlow<Boolean?> = _onboarded.asStateFlow()

    init {
        viewModelScope.launch {
            _onboarded.value = userProfileRepository.get().isOnboarded
        }
    }

    /** 「保存并开始」：存下填的项，并把体重 / 体脂记成今天的数据。 */
    fun complete(form: ProfileFormState) {
        viewModelScope.launch {
            val today = today()
            userProfileRepository.save(gender = form.gender, birthday = form.birthday)
            form.weight?.let { bodyMetricRepository.recordWeight(today, it) }
            form.bodyFat?.let { bodyMetricRepository.recordBodyFat(today, it) }
            userProfileRepository.markOnboarded(today)
            _onboarded.value = true
        }
    }

    /** 「跳过」：什么都不写，只记下引导已经走过。 */
    fun skip() {
        viewModelScope.launch {
            userProfileRepository.markOnboarded(today())
            _onboarded.value = true
        }
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
