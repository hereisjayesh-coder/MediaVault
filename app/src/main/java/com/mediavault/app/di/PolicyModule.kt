package com.mediavault.app.di

import com.mediavault.app.policy.AndroidNetworkPolicyManager
import com.mediavault.core.domain.network.NetworkPolicyManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PolicyModule {

    @Binds
    @Singleton
    abstract fun bindNetworkPolicyManager(impl: AndroidNetworkPolicyManager): NetworkPolicyManager
}
