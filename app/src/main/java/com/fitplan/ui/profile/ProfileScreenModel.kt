package com.fitplan.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.model.ActivityLevel
import com.fitplan.domain.model.BodyMetric
import com.fitplan.domain.model.EnergyExpenditure
import com.fitplan.domain.model.Gender
import com.fitplan.domain.model.UserProfile
import com.fitplan.domain.model.estimateEnergyExpenditure
import com.fitplan.domain.repository.BodyMetricRepository
import com.fitplan.domain.repository.UserProfileRepository
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

/** 「我的 → 个人信息」页的数据：个人信息本身 + 最近一次体重 / 体脂 + 代谢估算。 */
data class ProfileUiState(
    val profile: UserProfile,
    val latestWeight: BodyMetric?,
    val latestBodyFat: BodyMetric?,
    val energy: EnergyExpenditure?,
)

/**
 * 个人信息页：展示与修改性别、生日、身高、体重、体脂率、活动程度，并把体重 / 体脂率写成今天的记录。
 *
 * 体重 / 体脂留空表示「这次不改这一项」，不会删掉已有记录；
 * 代谢估算的体重取「最近一次体重记录」，所以没在这里改也会跟着已有记录走。
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ProfileScreenModel(
    private val userProfileRepository: UserProfileRepository,
    private val bodyMetricRepository: BodyMetricRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ProfileUiState?>(null)
    val state: StateFlow<ProfileUiState?> = _state.asStateFlow()

    /** 保存完成后置位一次，界面据此弹提示并把标记复位。 */
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = userProfileRepository.get()
            val latestWeight = bodyMetricRepository.getLatestWeight()
            _state.value = ProfileUiState(
                profile = profile,
                latestWeight = latestWeight,
                latestBodyFat = bodyMetricRepository.getLatestBodyFat(),
                energy = estimateEnergyExpenditure(
                    gender = profile.gender,
                    age = profile.ageOn(today()),
                    heightCm = profile.heightCm,
                    weightKg = latestWeight?.weight,
                    activityLevel = profile.activityLevel,
                ),
            )
        }
    }

    fun save(
        gender: Gender?,
        birthday: LocalDate?,
        heightCm: Double?,
        activityLevel: ActivityLevel?,
        weight: Double?,
        bodyFat: Double?,
    ) {
        viewModelScope.launch {
            val today = today()
            userProfileRepository.save(
                gender = gender,
                birthday = birthday,
                heightCm = heightCm,
                activityLevel = activityLevel,
            )
            weight?.let { bodyMetricRepository.recordWeight(today, it) }
            bodyFat?.let { bodyMetricRepository.recordBodyFat(today, it) }
            refresh()
            _saved.value = true
        }
    }

    fun consumeSaved() {
        _saved.value = false
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
