package com.minima.os.agent.di

import com.minima.os.agent.classifier.DeterministicClassifier
import com.minima.os.agent.classifier.IntentClassifier
import com.minima.os.agent.planner.DeterministicPlanner
import com.minima.os.agent.planner.Planner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds
    @Singleton
    abstract fun bindClassifier(impl: DeterministicClassifier): IntentClassifier

    @Binds
    @Singleton
    abstract fun bindPlanner(impl: DeterministicPlanner): Planner
}
