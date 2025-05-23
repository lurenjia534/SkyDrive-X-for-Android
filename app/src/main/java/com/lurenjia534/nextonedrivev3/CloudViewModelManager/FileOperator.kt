package com.lurenjia534.nextonedrivev3.CloudViewModelManager

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lurenjia534.nextonedrivev3.data.model.DriveItem
import com.lurenjia534.nextonedrivev3.data.model.Permission
import com.lurenjia534.nextonedrivev3.data.repository.OneDriveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileOperator(
    override val parentViewModel: ViewModel,
    override val viewModelScope: CoroutineScope,
    private val oneDriveRepository: OneDriveRepository,
    private val accountManager: AccountManager,
    private val fileNavigator: FileNavigator
) : BaseManager {

    // 删除状态封装类
    sealed class DeletingState {
        object Idle : DeletingState()
        data class Deleting(val itemName: String) : DeletingState()
        data class Success(val itemName: String) : DeletingState()
        data class Error(val message: String) : DeletingState()
    }

    // 共享状态封装类
    sealed class SharingState {
        object Idle : SharingState()
        data class Sharing(val itemName: String) : SharingState()
        data class Success(val permission: Permission) : SharingState()
        data class Error(val message: String) : SharingState()
    }

    // 移动状态封装类
    sealed class MovingState {
        object Idle : MovingState()
        data class Moving(val itemName: String) : MovingState()
        data class Success(val item: DriveItem) : MovingState()
        data class Error(val message: String) : MovingState()
    }

    // 添加共享选项封装类
    data class ShareOption(
        val type: String,     // "view" 或 "edit"
        val scope: String,    // "anonymous" 或 "organization"
        val label: String,    // 显示名称，如"任何人可查看"
        val description: String // 描述，如"任何获得链接的人都可以查看，无需登录"
    )

    // 复制状态
    sealed class CopyingState {
        object Idle : CopyingState()
        data class Copying(val itemName: String, val progress: Double) : CopyingState()
        data class Success(val itemName: String, val itemId: String) : CopyingState()
        data class Error(val message: String) : CopyingState()
    }

    // 删除状态流
    private val _deletingState = MutableStateFlow<DeletingState>(DeletingState.Idle)
    val deletingState: StateFlow<DeletingState> = _deletingState.asStateFlow()

    // 共享状态流
    private val _sharingState = MutableStateFlow<SharingState>(SharingState.Idle)
    val sharingState: StateFlow<SharingState> = _sharingState.asStateFlow()

    // 移动状态流
    private val _movingState = MutableStateFlow<MovingState>(MovingState.Idle)
    val movingState: StateFlow<MovingState> = _movingState.asStateFlow()

    // 创建状态流来跟踪复制进度
    private val _copyingState = MutableStateFlow<CopyingState>(CopyingState.Idle)
    val copyingState: StateFlow<CopyingState> = _copyingState.asStateFlow()

    // 复制监控URL
    private var copyMonitorUrl: String? = null

    // 错误信息
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // 可用的共享选项
    val shareOptions = listOf(
        ShareOption(
            type = "view",
            scope = "anonymous",
            label = "任何人可查看",
            description = "任何获得链接的人都可以查看，无需登录"
        ),
        ShareOption(
            type = "edit",
            scope = "anonymous",
            label = "任何人可编辑",
            description = "任何获得链接的人都可以查看和编辑，无需登录"
        ),
        ShareOption(
            type = "view",
            scope = "organization",
            label = "组织内可查看",
            description = "仅组织内的人可以使用此链接查看"
        ),
        ShareOption(
            type = "edit",
            scope = "organization",
            label = "组织内可编辑",
            description = "仅组织内的人可以使用此链接查看和编辑"
        )
    )

    /**
     * 创建文件夹
     */
    fun createFolder(folderName: String) {
        if (folderName.isBlank()) {
            _errorMessage.value = "文件夹名称不能为空"
            return
        }

        viewModelScope.launch {
            try {
                val token = accountManager.getCurrentToken() ?: ""
                val currentFolderId = fileNavigator.currentFolderId.value ?: "root"

                val result = oneDriveRepository.createFolder(
                    token = token,
                    parentId = currentFolderId,
                    folderName = folderName
                )

                result.onSuccess { item ->
                    _errorMessage.value = null
                    // 刷新文件列表
                    fileNavigator.refreshCurrentFolder()
                }.onFailure { error ->
                    val errorMsg = "创建文件夹失败: ${error.message}"
                    _errorMessage.value = errorMsg

                    // 检查是否是token过期问题并处理
                    if (error.message?.contains("token is expired") == true ||
                        error.message?.contains("InvalidAuthenticationToken") == true) {
                        accountManager.refreshTokenAndRetry { createFolder(folderName) }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "创建文件夹时发生错误: ${e.message}"
                _errorMessage.value = errorMsg
            }
        }
    }

    /**
     * 删除文件或文件夹
     */
    fun deleteItem(item: DriveItem) {
        viewModelScope.launch {
            try {
                _deletingState.value = DeletingState.Deleting(item.name)

                val token = accountManager.getCurrentToken() ?: ""

                val result = oneDriveRepository.deleteItem(
                    token = token,
                    itemId = item.id
                )

                result.onSuccess {
                    Log.d("FileOperator", "删除成功: ${item.name}")
                    _deletingState.value = DeletingState.Success(item.name)
                    // 刷新文件列表
                    fileNavigator.refreshCurrentFolder()
                }.onFailure { error ->
                    val errorMsg = "删除失败: ${error.message}"
                    Log.e("FileOperator", errorMsg)
                    _errorMessage.value = errorMsg
                    _deletingState.value = DeletingState.Error(errorMsg)

                    // 检查是否是token过期问题并处理
                    if (error.message?.contains("token is expired") == true ||
                        error.message?.contains("InvalidAuthenticationToken") == true) {
                        accountManager.refreshTokenAndRetry { deleteItem(item) }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "删除时发生错误: ${e.message}"
                Log.e("FileOperator", errorMsg, e)
                _errorMessage.value = errorMsg
                _deletingState.value = DeletingState.Error(errorMsg)
            }
        }
    }

    /**
     * 创建文件或文件夹的共享链接
     * @param item 要共享的DriveItem
     * @param type 链接类型，默认为"view"(只读)
     * @param scope 链接范围，默认为"anonymous"(匿名访问)
     */
    fun shareItem(
        item: DriveItem,
        type: String = "view",
        scope: String = "anonymous"
    ) {
        viewModelScope.launch {
            try {
                _sharingState.value = SharingState.Sharing(item.name)

                val token = accountManager.getCurrentToken() ?: ""

                val result = oneDriveRepository.createShareLink(
                    token = token,
                    itemId = item.id,
                    linkType = type,
                    scope = scope
                )

                result.onSuccess { permission ->
                    Log.d("FileOperator", "共享链接创建成功: ${item.name}, URL: ${permission.link.webUrl}")
                    _sharingState.value = SharingState.Success(permission)
                }.onFailure { error ->
                    val errorMsg = "创建共享链接失败: ${error.message}"
                    Log.e("FileOperator", errorMsg)
                    _errorMessage.value = errorMsg
                    _sharingState.value = SharingState.Error(errorMsg)

                    // 检查是否是token过期问题并处理
                    if (error.message?.contains("token is expired") == true ||
                        error.message?.contains("InvalidAuthenticationToken") == true) {
                        accountManager.refreshTokenAndRetry { shareItem(item, type, scope) }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "创建共享链接时发生错误: ${e.message}"
                Log.e("FileOperator", errorMsg, e)
                _errorMessage.value = errorMsg
                _sharingState.value = SharingState.Error(errorMsg)
            }
        }
    }

    /**
     * 移动文件或文件夹
     */
    fun moveItem(item: DriveItem, destinationFolderId: String) {
        viewModelScope.launch {
            try {
                _movingState.value = MovingState.Moving(item.name)

                val token = accountManager.getCurrentToken() ?: ""

                val result = oneDriveRepository.moveItem(
                    token = token,
                    itemId = item.id,
                    destinationFolderId = destinationFolderId
                )

                result.onSuccess { movedItem ->
                    Log.d("FileOperator", "移动成功: ${item.name} -> 文件夹: $destinationFolderId")
                    _movingState.value = MovingState.Success(movedItem)

                    // 移动成功后刷新当前文件夹
                    fileNavigator.refreshCurrentFolder()
                }.onFailure { error ->
                    val errorMsg = "移动失败: ${error.message}"
                    Log.e("FileOperator", errorMsg)
                    _errorMessage.value = errorMsg
                    _movingState.value = MovingState.Error(errorMsg)

                    // 检查是否是token过期问题并处理
                    if (error.message?.contains("token is expired") == true ||
                        error.message?.contains("InvalidAuthenticationToken") == true) {
                        accountManager.refreshTokenAndRetry { moveItem(item, destinationFolderId) }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "移动时发生错误: ${e.message}"
                Log.e("FileOperator", errorMsg, e)
                _errorMessage.value = errorMsg
                _movingState.value = MovingState.Error(errorMsg)
            }
        }
    }

    /**
     * 复制文件或文件夹
     * @param item 要复制的项目
     * @param destinationFolderId 目标文件夹ID
     * @param newName 可选的新名称
     */
    fun copyItem(item: DriveItem, destinationFolderId: String, newName: String? = null) {
        viewModelScope.launch {
            try {
                _copyingState.value = CopyingState.Copying(item.name, 0.0)
                
                val token = accountManager.getCurrentToken()
                if (token.isNullOrEmpty()) {
                    _copyingState.value = CopyingState.Error("无法获取访问令牌")
                    return@launch
                }
                
                // 添加日志输出帮助调试
                Log.d("FileOperator", "正在复制: ${item.name} 到目标文件夹: $destinationFolderId 新名称: $newName")
                
                // 调用复制方法
                val result = oneDriveRepository.initiateItemCopy(
                    token = token,
                    itemId = item.id,
                    destinationFolderId = destinationFolderId,
                    newName = newName
                )
                
                if (result.isSuccess) {
                    // 获取监控URL
                    copyMonitorUrl = result.getOrNull()
                    if (copyMonitorUrl != null) {
                        // 开始轮询复制状态
                        pollCopyStatus(item.name)
                    } else {
                        _copyingState.value = CopyingState.Error("无法获取复制状态URL")
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "未知错误"
                    _copyingState.value = CopyingState.Error("复制失败: $errorMsg")
                    _errorMessage.value = "复制失败: $errorMsg"
                }
            } catch (e: Exception) {
                _copyingState.value = CopyingState.Error("复制过程出错: ${e.message}")
                _errorMessage.value = "复制过程出错: ${e.message}"
            }
        }
    }
    
    /**
     * 轮询复制状态
     */
    private suspend fun pollCopyStatus(itemName: String) {
        val monitorUrl = copyMonitorUrl ?: return
        
        try {
            val result = oneDriveRepository.checkCopyStatus(monitorUrl)
            
            if (result.isSuccess) {
                val status = result.getOrNull()
                
                when (status?.status) {
                    "completed" -> {
                        _copyingState.value = CopyingState.Success(
                            itemName = itemName,
                            itemId = status.resourceId ?: ""
                        )
                        fileNavigator.refreshCurrentFolder()
                    }
                    "failed" -> {
                        val errorMsg = status.error?.message ?: "未知错误"
                        _copyingState.value = CopyingState.Error("复制失败: $errorMsg")
                    }
                    "inProgress", "notStarted" -> {
                        // 更新进度
                        _copyingState.value = CopyingState.Copying(
                            itemName = itemName,
                            progress = status.percentageComplete ?: 0.0
                        )
                        
                        // 延迟一段时间后再次轮询
                        kotlinx.coroutines.delay(2000)
                        pollCopyStatus(itemName)
                    }
                    else -> {
                        _copyingState.value = CopyingState.Error("未知的复制状态: ${status?.status}")
                    }
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "未知错误"
                _copyingState.value = CopyingState.Error("获取复制状态失败: $errorMsg")
            }
        } catch (e: Exception) {
            _copyingState.value = CopyingState.Error("轮询复制状态出错: ${e.message}")
        }
    }
}