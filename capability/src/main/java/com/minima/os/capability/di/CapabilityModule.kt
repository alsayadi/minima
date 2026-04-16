package com.minima.os.capability.di

import com.minima.os.core.executor.CapabilityExecutor
import com.minima.os.capability.calendar.CalendarCapability
import com.minima.os.capability.commerce.CommerceCapability
import com.minima.os.capability.messaging.MessagingCapability
import com.minima.os.capability.notification.NotificationCapability
import com.minima.os.capability.registry.CapabilityProvider
import com.minima.os.capability.registry.CapabilityRegistry
import com.minima.os.capability.chat.ChatCapability
import com.minima.os.capability.memory.MemoryCapability
import com.minima.os.capability.system.SystemCapability
import com.minima.os.capability.weather.WeatherCapability
import com.minima.os.capability.alarm.AlarmCapability
import com.minima.os.capability.contacts.ContactsCapability
import com.minima.os.capability.convert.ConvertCapability
import com.minima.os.core.bus.NotificationHub
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CapabilityBindsModule {

    @Binds
    @Singleton
    abstract fun bindCapabilityExecutor(registry: CapabilityRegistry): CapabilityExecutor
}

@Module
@InstallIn(SingletonComponent::class)
object CapabilityProvidesModule {

    @Provides
    @Singleton
    fun provideCapabilityMap(
        calendar: CalendarCapability,
        notification: NotificationCapability,
        messaging: MessagingCapability,
        commerce: CommerceCapability,
        system: SystemCapability,
        memory: MemoryCapability,
        chat: ChatCapability,
        weather: WeatherCapability,
        alarm: AlarmCapability,
        contacts: ContactsCapability,
        convert: ConvertCapability
    ): Map<String, @JvmSuppressWildcards CapabilityProvider> {
        // Wire the live-notifications source. The capability doesn't depend on
        // NotificationHub directly (it's a pull hook), so we plug it in here at
        // graph-construction time. The hub is populated by the listener service.
        notification.notificationSource = { NotificationHub.notifications.value }

        return mapOf(
            "calendar" to calendar,
            "notification" to notification,
            "messaging" to messaging,
            "commerce" to commerce,
            "system" to system,
            "memory" to memory,
            "chat" to chat,
            "weather" to weather,
            "alarm" to alarm,
            "contacts" to contacts,
            "convert" to convert
        )
    }
}
