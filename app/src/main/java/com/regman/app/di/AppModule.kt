package com.regman.app.di

import android.content.Context
import androidx.room.Room
import com.regman.app.data.crypto.KeystoreAccountCipher
import com.regman.app.data.db.AppDatabase
import com.regman.app.data.http.CronetHttpClientFactory
import com.regman.app.data.repo.AccountRepository
import com.regman.app.data.repo.TaskRepository
import com.regman.app.data.settings.SettingsRepository
import com.regman.core.crypto.AccountCipher
import com.regman.core.engine.CaptchaHolder
import com.regman.core.engine.DefaultRegisterOrchestrator
import com.regman.core.engine.RegisterOrchestrator
import com.regman.core.engine.RemoteConfig
import com.regman.core.http.HttpClient
import com.regman.core.mailbox.MailboxPool
import com.regman.core.platform.PlatformRegistry
import com.regman.core.platform.kiro.KiroConfigSource
import com.regman.core.platform.kiro.KiroPlatform
import com.regman.core.proxy.ProxyPool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun keystoreCipher(): KeystoreAccountCipher = KeystoreAccountCipher()

    @Provides @Singleton
    fun cipher(c: KeystoreAccountCipher): AccountCipher = c

    @Provides @Singleton
    fun captchaHolder() = CaptchaHolder()

    @Provides @Singleton
    fun httpFactory(@ApplicationContext ctx: Context): CronetHttpClientFactory = CronetHttpClientFactory(ctx)

    @Provides @Singleton
    fun baseHttp(factory: CronetHttpClientFactory): HttpClient = factory.create(null)

    @Provides @Singleton
    fun proxyPool() = ProxyPool()

    @Provides @Singleton
    fun mailboxPool() = MailboxPool()

    @Provides @Singleton
    fun kiroConfigSource(http: HttpClient, settings: SettingsRepository): KiroConfigSource {
        // 设置页配置的远端地址优先；未配置时用内置兜底
        val url = runBlocking { settings.remoteConfigUrl.first() }.ifBlank { DEFAULT_REMOTE }
        return KiroConfigSource(RemoteConfig(http, KIRO_FALLBACK), url)
    }

    @Provides @Singleton @IntoSet
    fun kiroPlatform(factory: CronetHttpClientFactory, cfg: KiroConfigSource): com.regman.core.platform.PlatformPlugin =
        KiroPlatform({ proxy -> factory.create(proxy) }, cfg)

    @Provides @Singleton
    fun platformRegistry(plugins: Set<@JvmSuppressWildcards com.regman.core.platform.PlatformPlugin>) =
        PlatformRegistry(plugins)

    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "regman.db").build()

    @Provides fun accountDao(db: AppDatabase) = db.accountDao()
    @Provides fun taskDao(db: AppDatabase) = db.taskDao()
    @Provides fun proxyDao(db: AppDatabase) = db.proxyDao()
    @Provides fun mailboxDao(db: AppDatabase) = db.mailboxDao()

    @Provides @Singleton
    fun accountRepo(dao: com.regman.app.data.db.AccountDao, cipher: AccountCipher, registry: PlatformRegistry) =
        AccountRepository(dao, cipher, registry)

    @Provides @Singleton
    fun taskRepo(dao: com.regman.app.data.db.TaskDao) = TaskRepository(dao)

    @Provides @Singleton
    fun orchestrator(
        scope: CoroutineScope,
        proxyPool: ProxyPool,
        mailboxPool: MailboxPool,
        captchaHolder: CaptchaHolder,
        repo: AccountRepository,
    ): RegisterOrchestrator =
        DefaultRegisterOrchestrator(scope, proxyPool, mailboxPool, captchaHolder, onAccount = { repo.saveDraft(it) })

    // TODO: 换成你自己的可写地址（Gist/对象存储）
    private const val DEFAULT_REMOTE = "https://example.com/regman/config/kiro.json"
    private val KIRO_FALLBACK: String = """
        {"oidcIssuer":"","oidcRegistrationUrl":"","deviceAuthorizationUrl":"","tokenUrl":"",
         "sendCodeUrl":"","verifyCodeUrl":"","setPasswordUrl":"",
         "awsAuthorizeUrl":"","createIdentityUrl":"","awsClientIdArn":"","awsRequestUri":"","awsIdentityStoreId":"",
         "socialAuthorizeUrl":"https://prod.us-east-1.auth.desktop.kiro.dev/login",
         "socialTokenUrl":"https://prod.us-east-1.auth.desktop.kiro.dev/oauth/token",
         "socialRefreshUrl":"https://prod.us-east-1.auth.desktop.kiro.dev/refreshToken",
         "socialRedirectUri":"kiro://kiro.kiroAgent/authenticate-success",
         "socialPortalUrl":"https://app.kiro.dev/signin",
         "kiroClientName":"kiro-oauth-client",
         "kiroScopes":"codewhisperer:completions codewhisperer:analysis codewhisperer:conversations",
         "hcaptchaSiteKey":"","apiBase":"","trialActivateUrl":"","quotaQueryUrl":""}
    """.trimIndent()
}
