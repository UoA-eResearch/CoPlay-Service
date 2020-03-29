package info.dvkr.screenstream.data.other

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.text.TextUtils
import androidx.annotation.WorkerThread
import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

@WorkerThread
class AndroidManifestService(private val packageManager: PackageManager, private val packageName: String, private val packagePath: String) {

    fun loadAndroidManifest(): String {

        val manifest = readManifest(packageManager, packageName, packagePath)
        return formatManifest(manifest)
    }

    private fun readManifest(packageManager: PackageManager, packageName: String, packagePath: String): String {
        val stringBuilder = StringBuilder()
        try {
            val apkResources = try {

                packageManager.getResourcesForApplication(packageName)

            } catch (exception: PackageManager.NameNotFoundException){

                packageManager.getPackageArchiveInfo(packagePath,0)?.let {
                    packageManager.getResourcesForApplication(it.applicationInfo)
                }

            }

            val parser = apkResources?.assets?.openXmlResourceParser("AndroidManifest.xml") ?: return ""

            var eventType: Int = parser.next()

            while (eventType != XmlResourceParser.END_DOCUMENT) {
                // start tag found
                if (eventType == XmlResourceParser.START_TAG) {
                    //start with opening element and writing its name
                    stringBuilder.append("<").append(parser.name)

                    //for each attribute in given element append attrName="attrValue"
                    for (attribute in 0 until parser.attributeCount) {
                        val attributeName = parser.getAttributeName(attribute)
                        val attributeValue = getAttributeValue(attributeName, parser.getAttributeValue(attribute), apkResources)

                        stringBuilder.append(" ").append(attributeName).append("=\"").append(attributeValue).append("\"")
                    }

                    stringBuilder.append(">")

                    if (parser.text != null) {
                        // if there is  body of xml element, add it there
                        stringBuilder.append(parser.text)
                    }
                } else if (eventType == XmlResourceParser.END_TAG) {
                    stringBuilder.append("</").append(parser.name).append(">")

                }

                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return stringBuilder.toString()
    }


    private fun formatManifest(manifest: String): String {
        return try {
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            val result = StreamResult(StringWriter())
            val source = StreamSource(ByteArrayInputStream(manifest.toByteArray(charset("UTF-8"))))
            transformer.transform(source, result)
            result.writer.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun getAttributeValue(attributeName: String, attributeValue: String, resources: Resources): String {
        if (attributeValue.startsWith("@")) {
            try {
                val id = Integer.valueOf(attributeValue.substring(1))

                val value: String = when (attributeName) {
                    "theme", "resource" -> resources.getResourceEntryName(id)
                    else -> resources.getString(id)
                }

                return TextUtils.htmlEncode(value)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
        return attributeValue
    }

    companion object {

        /**
         * naive approach. Just grab all the actions and make them available.
         */
        fun getAppActions(applicationInfo: ApplicationInfo, packageManager: PackageManager):  MutableList<String>
        {
            val actions: MutableList<String> = ArrayList()
            try {
                val apkResources = packageManager.getResourcesForApplication(applicationInfo)
                val parser = apkResources.assets.openXmlResourceParser("AndroidManifest.xml")
                var eventType = -1
                var receiver : String

                while (eventType != XmlResourceParser.END_DOCUMENT) {
                    if (eventType == XmlResourceParser.START_TAG) {
                        if ("action".equals(parser.name))
                            actions.add(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name"))
                    }
                    eventType = parser.next()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            return actions;
        }
    }
}
