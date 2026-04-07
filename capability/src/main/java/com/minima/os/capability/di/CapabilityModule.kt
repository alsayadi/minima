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
        chat: ChatCapability
    ): Map<String, @JvmSuppressWildcards CapabilityProvider> {
        return mapOf(
            "calendar" to calendar,
            "notification" to notification,
            "messaging" to messaging,
            "commerce" to commerce,
            "system" to system,
            "memory" to memory,
            "chat" to chat
        )
    }
}
