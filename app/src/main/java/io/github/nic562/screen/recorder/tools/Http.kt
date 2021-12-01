package io.github.nic562.screen.recorder.tools

import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.HashMap

/**
 * Created by Nic on 2020/9/13.
 */
object Http {

    interface DownloadProcessListener {
        fun onDownloadProcessing(total: Long, curr: Long)
    }

    private fun getConnection(
        url: String,
        headers: HashMap<String, String>? = null,
        connTimeoutInMillis: Int = 5000,
        readTimeoutInMillis: Int = 5000
    ): HttpURLConnection {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connTimeoutInMillis
            readTimeout = readTimeoutInMillis
            if (headers != null) {
                for (x in headers.keys) {
                    this.setRequestProperty(x, headers[x])
                }
            }
            return this
        }
    }

    private class FlushedInputStream(inStream: InputStream) : FilterInputStream(inStream) {

        override fun skip(n: Long): Long {
            var totalBytesSkipped = 0L
            while (totalBytesSkipped < n) {
                var bytesSkipped = `in`.skip(n - totalBytesSkipped)
                if (bytesSkipped == 0L) {
                    if (read() < 0) {
                        break // we reached EOF
                    } else {
                        bytesSkipped = 1
                    }
                }
                totalBytesSkipped += bytesSkipped
            }
            return totalBytesSkipped
        }
    }

    private fun readStream(ins: InputStream, charsetName: String? = null): String {
        var input: InputStreamReader? = null
        var bf: BufferedReader? = null
        try {
            val s = FlushedInputStream(ins)
            input = if (charsetName.isNullOrEmpty())
                InputStreamReader(s)
            else
                InputStreamReader(s, charsetName)
            bf = BufferedReader(input)
            val sb = StringBuilder()
            var tmp: String?
            while (bf.readLine().apply {
                    tmp = this
                } != null) {
                if (tmp != null)
                    sb.append(tmp)
            }
            return sb.toString()
        } finally {
            input?.close()
            bf?.close()
        }
    }

    private val charsetPattern = Pattern.compile("charset=(.+)")

    private fun readResponseCharset(respHeaders: Map<String, List<String>>): String {
        for (k in respHeaders.keys) {
            if (k == "Content-Type") {
                for (v in respHeaders[k] ?: continue) {
                    val m = charsetPattern.matcher(v)
                    if (m.find()) {
                        return m.group(m.groupCount()) ?: continue
                    }
                }
            }
        }
        return ""
    }

    open class HttpError(val code: Int, msg: String) : Exception(msg)

    class NeedRestartError(file: String) : HttpError(416, file)

    fun downloadFile(
        url: String,
        savingFile: File,
        headers: HashMap<String, String>,
        timeoutInMillis: Int,
        listener: DownloadProcessListener? = null,
        autoRetry: Boolean = true
    ): String {
        val append: Boolean
        val rangeSize =
            if (savingFile.exists()) {
                append = true
                savingFile.length()
            } else {
                append = false
                0
            }
        FileOutputStream(savingFile, append).use {
            try {
                return downloadFile(url, it, headers, timeoutInMillis, listener, rangeSize)
            } catch (e: NeedRestartError) {
                if (autoRetry) {
                    savingFile.delete()
                    return downloadFile(
                        url,
                        savingFile,
                        headers,
                        timeoutInMillis,
                        listener,
                        autoRetry
                    )
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * @param url 文件请求下载地址
     * @param fileOutStream 文件输出流
     * @param rangeSize 可续传的文件，已经存在的文件大小
     * @return 下载完成后，请求地址请求中获取的文件名，若下载失败，则不返回
     */
    private fun downloadFile(
        url: String,
        fileOutStream: FileOutputStream,
        headers: HashMap<String, String>,
        timeoutInMillis: Int,
        listener: DownloadProcessListener? = null,
        rangeSize: Long = 0
    ): String {
        if (rangeSize > 0) {
            headers["Range"] = "bytes=$rangeSize-2147483647"
        } else {
            headers.remove("Range")
        }
        val conn = getConnection(url, headers, timeoutInMillis)
        try {
            val code = conn.responseCode
            if (code == 416) {
                // 若文件名存在，并且续传失败，需要重新下载。
                throw NeedRestartError("`$url` download range from $rangeSize error, please delete the file and retry!")
            }
            val charset = readResponseCharset(conn.headerFields)
            if (code >= 400) {
                throw HttpError(
                    code,
                    "`%url` request error: $code ${
                        readStream(conn.errorStream, charset)
                    }"
                )
            }
            return downloadFileReadData(conn, fileOutStream, listener, rangeSize)
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFileReadData(
        conn: HttpURLConnection,
        fileOutStream: FileOutputStream,
        listener: DownloadProcessListener? = null,
        rangeSize: Long = 0
    ): String {
        var filename: String? = conn.getHeaderField("Content-Disposition")
        filename = if (filename == null) {
            ""
        } else {
            val m = "filename=\""
            val x = filename.indexOf(m)
            if (x == -1) {
                ""
            } else {
                filename.substring(x + m.length).replace("\"", "")
            }
        }

        val total = rangeSize + conn.contentLength

        conn.inputStream.use {
            var sum = rangeSize
            var len: Int
            val buf = ByteArray(512)
            while (it.read(buf).apply {
                    len = this
                } != -1) {
                sum += len
                fileOutStream.write(buf, 0, len)
                listener?.onDownloadProcessing(total, sum)
            }
            fileOutStream.flush()
            return filename
        }
    }

    interface ReadFileProgress {
        fun progress(total: Long, p: Long)
        fun keepWorking(): Boolean
    }

    private const val end = "\r\n"
    private const val end2 = "\r\n\r\n"
    private const val twoHyphens = "--"
    private const val boundary = "*****"

    private fun writeFileToDataOutputStream(
        file: File,
        fileArgumentName: String,
        fileContentType: String,
        dop: DataOutputStream,
        progressListener: ReadFileProgress? = null
    ) {
        dop.apply {
            writeSplit(this)
            writeBytes("Content-Disposition: form-data; name=\"$fileArgumentName\";filename=\"${file.name}\"")
            writeBytes(end)
            writeBytes("Content-Type: $fileContentType")
            writeBytes(end2)
            val total = file.length()
            FileInputStream(file).use {
                val bf = ByteArray(1024)
                var len: Int
                var sum = 0L
                while (it.read(bf).apply { len = this } != -1) {
                    if (progressListener?.keepWorking() == false) {
                        throw InterruptedIOException("Writing file io is interrupted!")
                    }
                    sum += len
                    write(bf, 0, len)
                    progressListener?.progress(total, sum)
                }
            }
            writeBytes(end)
        }
    }

    interface ReadFilesProgress {
        fun progress(
            fileCount: Int,
            currentFileIdx: Int,
            currentFileTotal: Long,
            currentFileP: Long
        )

        fun keepWorking(): Boolean
    }

    fun upload(
        url: String,
        fileArgumentName: String,
        fileContentType: String,
        vararg files: File,
        args: TreeMap<String, String>? = null,
        headers: HashMap<String, String>? = null,
        timeout: Int = 60000,
        progressListener: ReadFilesProgress? = null,
        encoding: Boolean = true
    ): String {
        if (files.isEmpty()) {
            throw Exception("No file will be upload! Please check `files` arguments.")
        }
        getConnection(url, headers, timeout).apply {
            doInput = true
            doOutput = true
            useCaches = false
            requestMethod = "POST"
            setRequestProperty("Charset", "UTF-8")
            setRequestProperty("Content-Type", "multipart/form-data;boundary=$boundary")

            try {
                DataOutputStream(this.outputStream).use {
                    if (args != null)
                        for (k in args.keys) {
                            writeSplit(it)
                            it.writeBytes("Content-Disposition: form-data; name=\"$k\"")
                            it.writeBytes(end2)
                            if (encoding)
                                it.writeBytes(URLEncoder.encode(args[k], "UTF8"))
                            else
                                it.writeBytes(args[k])
                            it.writeBytes(end)
                        }
                    for (idx in files.indices) {
                        val f = files[idx]
                        val pl = if (progressListener == null) null else object : ReadFileProgress {
                            override fun progress(total: Long, p: Long) {
                                progressListener.progress(files.count(), idx, total, p)
                            }

                            override fun keepWorking(): Boolean {
                                return progressListener.keepWorking()
                            }
                        }
                        writeFileToDataOutputStream(f, fileArgumentName, fileContentType, it, pl)
                    }
                    it.writeBytes(twoHyphens)
                    it.writeBytes(boundary)
                    it.writeBytes(twoHyphens)
                    it.flush()
                }

                val charset = readResponseCharset(headerFields)
                if (responseCode >= 400) {
                    throw HttpError(
                        responseCode,
                        "http err\t $responseCode \n ${
                            readStream(errorStream, charset)
                        }"
                    )
                }
                return readStream(inputStream, charset)
            } finally {
                disconnect()
            }
        }
    }

    private fun writeSplit(ds: DataOutputStream) {
        ds.apply {
            writeBytes(twoHyphens)
            writeBytes(boundary)
            writeBytes(end)
        }
    }
}