package com.example.ai_assis.di

import com.example.ai_assis.data.remote.AuthRepository
import com.example.ai_assis.data.remote.AuthRepositoryImpl
import com.example.ai_assis.data.remote.FirestoreUsageRepository
import com.example.ai_assis.data.remote.FirestoreUsageRepositoryImpl
import com.example.ai_assis.payment.FirebaseCallableNames
import com.example.ai_assis.payment.SubscriptionPaymentRepository
import com.example.ai_assis.payment.SubscriptionPaymentRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseProviderModule {
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance(FirebaseCallableNames.FUNCTIONS_REGION)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseBindingsModule {
    @Binds
    @Singleton
    abstract fun bindFirestoreUsageRepository(
        impl: FirestoreUsageRepositoryImpl,
    ): FirestoreUsageRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl,
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionPaymentRepository(
        impl: SubscriptionPaymentRepositoryImpl,
    ): SubscriptionPaymentRepository
}
