package com.lurenjia534.nextonedrivev3.AuthRepository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import androidx.core.content.edit

// 账户数据类
data class AccountInfo(
    val id: String,
    val name: String,
    val token: String
)

// 添加一个数据类来表示认证消息
data class AuthMessage(
    val message: String,
    val isError: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authenticationManager: AuthenticationManager,
    val accessTokenState: MutableState<String?>,
    val isMsalInitializedState: MutableState<Boolean>,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // 保存所有账户的LiveData
    private val _accounts = MutableLiveData<List<AccountInfo>>(emptyList())
    val accounts: LiveData<List<AccountInfo>> = _accounts
    
    // 当前正在认证的账户名称
    private var currentAuthAccount: String? = null

    // 添加一个LiveData来传递认证消息
    private val _authMessage = MutableLiveData<AuthMessage?>()
    val authMessage: LiveData<AuthMessage?> = _authMessage

    // 深色模式状态
    private val _isDarkMode = MutableLiveData<Boolean>()
    val isDarkMode: LiveData<Boolean> = _isDarkMode

    init {
        authenticationManager.initializeMSAL()
        // 加载已保存的账户
        loadSavedAccounts()
        
        // 加载深色模式偏好设置
        loadDarkModePreference()
        
        // 添加：启动自动令牌刷新
        scheduleTokenRefreshWorker()
    }

    private fun loadSavedAccounts() {
        // 首先尝试加载多账户信息
        val accounts = tokenManager.getMultipleAccounts()
        if (accounts.isNotEmpty()) {
            _accounts.value = accounts
            return
        }
        
        // 向后兼容：如果没有多账户信息，尝试加载单个账户
        val accountId = tokenManager.getAccountId()
        val token = tokenManager.getAccessToken()
        if (accountId != null && token != null) {
            val name = tokenManager.getAccountName() ?: "我的OneDrive" 
            _accounts.value = listOf(AccountInfo(accountId, name, token))
        }
    }

    fun initiateAuthFlow(activity: Activity, accountName: String) {
        currentAuthAccount = accountName
        
        // 检查MSAL是否已初始化
        if (!isMsalInitializedState.value) {
            Log.d("AuthViewModel", "MSAL未初始化，正在初始化...")
            authenticationManager.initializeMSAL()
            // 给MSAL初始化一些时间
            CoroutineScope(Dispatchers.Main).launch {
                delay(500) // 等待500毫秒
                proceedWithAuth(activity)
            }
        } else {
            proceedWithAuth(activity)
        }
    }

    fun updateAccount(account: AccountInfo) {
        val currentAccounts = _accounts.value ?: emptyList()
        val updatedAccounts = currentAccounts.map {
            if (it.id == account.id) account else it
        }
        _accounts.value = updatedAccounts
        tokenManager.saveMultipleAccounts(updatedAccounts)

        // 如果更新的是当前活跃账户，也更新单账户存储
        if (tokenManager.getAccountId() == account.id) {
            tokenManager.saveAccountName(account.name)
        }

        // 发送成功消息
        _authMessage.value = AuthMessage(
            message = "账户\"${account.name}\"已更新",
            isError = false
        )
    }

    private fun proceedWithAuth(activity: Activity) {
        // 新添加的账户直接进行交互式认证
        Log.d("AuthViewModel", "开始交互式认证流程...")
        acquireTokenInteractive(activity)
    }
    
    private fun acquireTokenInteractive(activity: Activity) {
        val callback = object : AuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                // 使用当前账户名保存认证结果
                currentAuthAccount?.let { accountName ->
                    authenticationManager.saveAuthenticationResult(authenticationResult, accountName)
                } ?: run {
                    authenticationManager.saveAuthenticationResult(authenticationResult)
                }
                
                accessTokenState.value = authenticationResult.accessToken
                
                // 保存新账户
                currentAuthAccount?.let { accountName ->
                    val newAccount = AccountInfo(
                        id = authenticationResult.account.id,
                        name = accountName,
                        token = authenticationResult.accessToken
                    )
                    addNewAccount(newAccount)
                    
                    // 确保UI能立即更新
                    CoroutineScope(Dispatchers.Main).launch {
                        _accounts.value = _accounts.value  // 触发LiveData的更新
                    }
                }
                
                // 添加：在成功认证后再次调度令牌刷新
                scheduleTokenRefreshWorker()
                
                Log.d("MSAL Auth", "成功获取令牌: ${authenticationResult.accessToken}")
                
                // 添加成功消息
                _authMessage.value = AuthMessage(
                    message = "账户\"${currentAuthAccount ?: "新账户"}\"认证成功",
                    isError = false
                )
            }

            override fun onError(exception: MsalException) {
                Log.e("MSAL Auth Error", "认证失败: ${exception.message}")
                
                // 添加错误消息
                _authMessage.value = AuthMessage(
                    message = "认证失败: ${exception.localizedMessage ?: "未知错误"}",
                    isError = true
                )
            }

            override fun onCancel() {
                Log.d("MSAL Auth", "用户取消了认证")
                
                // 添加取消消息
                _authMessage.value = AuthMessage(
                    message = "认证已取消",
                    isError = true
                )
            }
        }

        authenticationManager.acquireTokenInteractive(activity, callback)
    }
    
    private fun acquireTokenSilent(activity: Activity, callbackProvider: AuthenticationCallbackProvider) {
        try {
            authenticationManager.acquireTokenSilent(callbackProvider.getSilentAuthCallback())
        } catch (e: Exception) {
            Log.e("AuthViewModel", "静默获取令牌失败，尝试交互式认证", e)
            acquireTokenInteractive(activity)
        }
    }
    
    private fun addNewAccount(accountInfo: AccountInfo) {
        val currentAccounts = _accounts.value ?: emptyList()
        // 检查账户是否已存在，如果已存在则更新
        val updatedAccounts = if (currentAccounts.any { it.id == accountInfo.id }) {
            currentAccounts.map { 
                if (it.id == accountInfo.id) accountInfo else it 
            }
        } else {
            currentAccounts + accountInfo
        }
        _accounts.value = updatedAccounts
        
        // 保存到TokenManager
        tokenManager.saveAccountId(accountInfo.id)
        tokenManager.saveAccessToken(accountInfo.token)
        tokenManager.saveAccountName(accountInfo.name)
        
        // 同时保存多账户信息
        tokenManager.saveMultipleAccounts(updatedAccounts)
        
        // 强制UI刷新
        _accounts.postValue(updatedAccounts)
    }

    // 可以添加一个方法来清除消息
    fun clearAuthMessage() {
        _authMessage.value = null
    }

    /**
     * 加载深色模式偏好设置
     */
    private fun loadDarkModePreference() {
        val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        _isDarkMode.value = sharedPreferences.getBoolean("dark_mode", false)
    }
    
    /**
     * 更新深色模式偏好设置
     */
    fun updateDarkMode(isDarkMode: Boolean) {
        _isDarkMode.value = isDarkMode
        
        // 保存到SharedPreferences
        val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        sharedPreferences.edit { putBoolean("dark_mode", isDarkMode) }
        
        // 发送深色模式已更新的消息
        _authMessage.value = AuthMessage(
            message = if (isDarkMode) "已切换到深色模式" else "已切换到浅色模式",
            isError = false
        )
    }

    /**
     * 检查令牌是否过期并处理
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun checkAndHandleTokenExpiration(errorMessage: String?, onTokenRefreshed: () -> Unit) {
        val needsRefresh = errorMessage?.contains("token is expired") == true || 
            errorMessage?.contains("Lifetime validation failed") == true || 
            errorMessage == null // 当errorMessage为null时，主动检查并刷新令牌
            
        if (needsRefresh) {
            Log.d("AuthViewModel", "检测到令牌需要刷新 ${errorMessage ?: "（主动刷新）"}")
            
            // 获取当前活跃账户ID
            val currentAccountId = tokenManager.getAccountId() ?: return
            
            // 启动协程执行令牌刷新
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val msalApp = authenticationManager.getMsalInstance() as? IMultipleAccountPublicClientApplication
                    if (msalApp == null) {
                        Log.e("AuthViewModel", "MSAL实例不可用")
                        return@launch
                    }
                    
                    // 获取账户
                    val account = msalApp.getAccount(currentAccountId)
                    if (account == null) {
                        Log.e("AuthViewModel", "找不到当前活跃账户")
                        return@launch
                    }
                    
                    // 尝试静默刷新
                    val result = suspendCancellableCoroutine<Boolean> { continuation ->
                        msalApp.acquireTokenSilentAsync(
                            arrayOf("User.Read", "Files.Read.All", "LicenseAssignment.Read.All"),
                            // 如果是家庭版请使用下方权限，家庭版不能获取许可证分配信息
                            // arrayOf("User.Read", "Files.Read.All", "Files.ReadWrite.All"),
                            account,
                            msalApp.configuration.defaultAuthority.authorityURL.toString(),
                            object : SilentAuthenticationCallback {
                                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                                    // 保存新令牌
                                    tokenManager.saveAccessToken(authenticationResult.accessToken)
                                    accessTokenState.value = authenticationResult.accessToken
                                    
                                    // 更新多账户存储
                                    val currentAccounts = tokenManager.getMultipleAccounts()
                                    val updatedAccounts = currentAccounts.map {
                                        if (it.id == currentAccountId) {
                                            AccountInfo(id = it.id, name = it.name, token = authenticationResult.accessToken)
                                        } else {
                                            it
                                        }
                                    }
                                    tokenManager.saveMultipleAccounts(updatedAccounts)
                                    
                                    // 刷新后更新UI显示的账户列表
                                    _accounts.postValue(updatedAccounts)
                                    
                                    Log.d("AuthViewModel", "令牌已成功刷新")
                                    continuation.resume(true) { 
                                        // 在协程取消时执行的代码
                                        Log.d("AuthViewModel", "令牌刷新被取消")
                                    }
                                }

                                override fun onError(exception: MsalException) {
                                    Log.e("AuthViewModel", "令牌刷新失败: ${exception.message}")
                                    continuation.resume(false) {
                                        // 在协程取消时执行的代码
                                        Log.e("AuthViewModel", "令牌刷新过程被取消: ${exception.message}")
                                    }
                                }
                            }
                        )
                    }
                    
                    // 在主线程调用回调
                    if (result) {
                        withContext(Dispatchers.Main) {
                            onTokenRefreshed()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AuthViewModel", "令牌刷新过程中出现异常: ${e.message}")
                }
            }
        } else {
            // 如果不需要刷新，直接调用回调
            onTokenRefreshed()
        }
    }

    /**
     * 手动刷新指定账户的令牌
     * @param accountId 要刷新的账户ID
     * @param callback 刷新结果回调，参数为是否成功
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun refreshTokenManually(accountId: String, callback: (Boolean) -> Unit) {
        Log.d("AuthViewModel", "开始手动刷新令牌，账户ID: $accountId")
        
        // 启动协程执行令牌刷新
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val msalApp = authenticationManager.getMsalInstance() as? IMultipleAccountPublicClientApplication
                if (msalApp == null) {
                    Log.e("AuthViewModel", "MSAL实例不可用")
                    withContext(Dispatchers.Main) { callback(false) }
                    return@launch
                }
                
                // 获取账户
                val account = msalApp.getAccount(accountId)
                if (account == null) {
                    Log.e("AuthViewModel", "找不到指定账户")
                    withContext(Dispatchers.Main) { callback(false) }
                    return@launch
                }
                
                // 尝试静默刷新
                val result = suspendCancellableCoroutine<Boolean> { continuation ->
                    msalApp.acquireTokenSilentAsync(
                        arrayOf("User.Read", "Files.Read.All", "LicenseAssignment.Read.All"),
                        account,
                        msalApp.configuration.defaultAuthority.authorityURL.toString(),
                        object : SilentAuthenticationCallback {
                            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                                // 保存新令牌
                                val newToken = authenticationResult.accessToken
                                Log.d("AuthViewModel", "令牌刷新成功")
                                
                                // 更新多账户存储
                                val currentAccounts = tokenManager.getMultipleAccounts()
                                val updatedAccounts = currentAccounts.map {
                                    if (it.id == accountId) {
                                        AccountInfo(id = it.id, name = it.name, token = newToken)
                                    } else {
                                        it
                                    }
                                }
                                tokenManager.saveMultipleAccounts(updatedAccounts)
                                
                                // 如果是当前活跃账户，也更新单账户存储
                                if (accountId == tokenManager.getAccountId()) {
                                    tokenManager.saveAccessToken(newToken)
                                    accessTokenState.value = newToken
                                }
                                
                                continuation.resume(true) { 
                                    Log.d("AuthViewModel", "令牌刷新协程被取消")
                                }
                            }

                            override fun onError(exception: MsalException) {
                                Log.e("AuthViewModel", "令牌刷新失败: ${exception.message}")
                                continuation.resume(false) {
                                    Log.e("AuthViewModel", "令牌刷新过程被取消: ${exception.message}")
                                }
                            }
                        }
                    )
                }
                
                // 在主线程调用回调
                withContext(Dispatchers.Main) {
                    callback(result)
                    
                    // 在成功刷新后更新UI中显示的账户列表
                    if (result) {
                        _accounts.value = tokenManager.getMultipleAccounts()
                        
                        // 添加成功消息
                        _authMessage.value = AuthMessage(
                            message = "账户令牌已成功刷新",
                            isError = false
                        )
                    } else {
                        // 添加失败消息
                        _authMessage.value = AuthMessage(
                            message = "账户令牌刷新失败",
                            isError = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "令牌刷新过程中出现异常: ${e.message}")
                withContext(Dispatchers.Main) { 
                    callback(false)
                    
                    // 添加异常消息
                    _authMessage.value = AuthMessage(
                        message = "令牌刷新出错: ${e.localizedMessage ?: "未知错误"}",
                        isError = true
                    )
                }
            }
        }
    }

    // 修改调度令牌刷新任务方法
    private fun scheduleTokenRefreshWorker() {
        // 检查是否有已保存的账户
        val accounts = tokenManager.getMultipleAccounts()
        val accountId = tokenManager.getAccountId()
        
        if (accounts.isNotEmpty() || accountId != null) {
            try {
                // 直接委托给AuthenticationManager处理令牌刷新调度
                // 它已经有了正确处理clientId的逻辑
                authenticationManager.scheduleTokenRefresh()
                Log.d("AuthViewModel", "已委托AuthenticationManager调度令牌自动刷新任务")
            } catch (e: Exception) {
                Log.e("AuthViewModel", "调度令牌刷新任务出错: ${e.message}")
            }
        }
    }
} 