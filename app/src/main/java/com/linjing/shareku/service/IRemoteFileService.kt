package com.linjing.shareku.service

import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

/**
 * Shizuku UserService 接口（手写 binder，替代 AIDL 生成）。
 *
 * 说明：proot 环境下 build-tools 的 aidl 工具（x86-64 + qemu 翻译）运行不稳定
 * （随机 Illegal instruction），因此手写标准 Binder 协议，跨进程能力与 AIDL 等价。
 */
interface IRemoteFileService : IInterface {

    /** 列出目录内容；每个条目 Bundle 含 name/isDirectory/size/path 四个键；失败返回 null */
    fun listDirectory(path: String): List<Bundle>?

    /** 读取文件指定范围内容（用于受限目录下载）；失败返回 null */
    fun readFile(path: String, offset: Long, size: Int): ByteArray?

    /** 文件信息；Bundle 含 size/isDirectory/exists/lastModified；失败返回 null */
    fun stat(path: String): Bundle?

    /** 服务端 Stub（Binder 实现），由 shell 身份的 UserService 实现 */
    abstract class Stub : Binder(), IRemoteFileService {

        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                TRANSACTION_listDirectory -> {
                    data.enforceInterface(DESCRIPTOR)
                    val path = data.readString() ?: ""
                    val result = listDirectory(path)
                    reply?.writeNoException()
                    if (result != null) {
                        reply?.writeInt(1)
                        reply?.writeList(result)
                    } else {
                        reply?.writeInt(0)
                    }
                    return true
                }
                TRANSACTION_readFile -> {
                    data.enforceInterface(DESCRIPTOR)
                    val path = data.readString() ?: ""
                    val offset = data.readLong()
                    val size = data.readInt()
                    val result = readFile(path, offset, size)
                    reply?.writeNoException()
                    if (result != null) {
                        reply?.writeInt(1)
                        reply?.writeByteArray(result)
                    } else {
                        reply?.writeInt(0)
                    }
                    return true
                }
                TRANSACTION_stat -> {
                    data.enforceInterface(DESCRIPTOR)
                    val path = data.readString() ?: ""
                    val result = stat(path)
                    reply?.writeNoException()
                    if (result != null) {
                        reply?.writeInt(1)
                        reply?.writeBundle(result)
                    } else {
                        reply?.writeInt(0)
                    }
                    return true
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
        }

        companion object {
            @JvmStatic
            fun asInterface(obj: IBinder?): IRemoteFileService? {
                if (obj == null) return null
                val iin = obj.queryLocalInterface(DESCRIPTOR)
                if (iin is IRemoteFileService) return iin
                return Proxy(obj)
            }
        }
    }

    /** 客户端代理：主进程通过它调用 shell 进程的 UserService */
    private class Proxy(private val remote: IBinder) : IRemoteFileService {

        override fun asBinder(): IBinder = remote

        override fun listDirectory(path: String): List<Bundle>? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(path)
                remote.transact(TRANSACTION_listDirectory, data, reply, 0)
                reply.readException()
                return if (reply.readInt() == 1) {
                    @Suppress("UNCHECKED_CAST")
                    reply.readArrayList(Bundle::class.java.classLoader) as? List<Bundle>
                } else {
                    null
                }
            } catch (e: Exception) {
                return null
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun readFile(path: String, offset: Long, size: Int): ByteArray? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(path)
                data.writeLong(offset)
                data.writeInt(size)
                remote.transact(TRANSACTION_readFile, data, reply, 0)
                reply.readException()
                return if (reply.readInt() == 1) {
                    reply.createByteArray()
                } else {
                    null
                }
            } catch (e: Exception) {
                return null
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun stat(path: String): Bundle? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(path)
                remote.transact(TRANSACTION_stat, data, reply, 0)
                reply.readException()
                return if (reply.readInt() == 1) {
                    reply.readBundle(Bundle::class.java.classLoader)
                } else {
                    null
                }
            } catch (e: Exception) {
                return null
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    companion object {
        const val DESCRIPTOR = "com.linjing.shareku.service.IRemoteFileService"
        const val TRANSACTION_listDirectory = 1
        const val TRANSACTION_readFile = 2
        const val TRANSACTION_stat = 3
    }
}