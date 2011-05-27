/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.layout;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttr;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.helpers.Transform;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * This layout outputs events in a HTML table.
 * <p/>
 * Appenders using this layout should have their encoding set to UTF-8 or UTF-16, otherwise events containing
 * non ASCII characters could result in corrupted log files.
 */
@Plugin(name="HTMLLayout",type="Core",elementType="layout",printObject=true)
public class HTMLLayout extends LayoutBase {

    protected static final int BUF_SIZE = 256;

    private static final String TRACE_PREFIX = "<br>&nbsp;&nbsp;&nbsp;&nbsp;";

    // Print no location info by default
    protected final boolean locationInfo;

    private static final String DEFAULT_TITLE = "Log4J Log Messages";

    private static final String DEFAULT_CONTENT_TYPE = "text/html";

    protected final String title;

    protected final String contentType;

    protected final Charset charset;

    public HTMLLayout(boolean locationInfo, String title, String contentType, Charset charset) {
        this.locationInfo = locationInfo;
        this.title = title;
        this.contentType = contentType;
        this.charset = charset;
    }

    public byte[] format(LogEvent event) {
        StringBuilder sbuf = new StringBuilder(BUF_SIZE);

        sbuf.append(LINE_SEP).append("<tr>").append(LINE_SEP);

        sbuf.append("<td>");
        sbuf.append(event.getMillis() - LoggerContext.getStartTime());
        sbuf.append("</td>").append(LINE_SEP);

        String escapedThread = Transform.escapeTags(event.getThreadName());
        sbuf.append("<td title=\"").append(escapedThread).append(" thread\">");
        sbuf.append(escapedThread);
        sbuf.append("</td>").append(LINE_SEP);

        sbuf.append("<td title=\"Level\">");
        if (event.getLevel().equals(Level.DEBUG)) {
            sbuf.append("<font color=\"#339933\">");
            sbuf.append(Transform.escapeTags(String.valueOf(event.getLevel())));
            sbuf.append("</font>");
        } else if (event.getLevel().isAtLeastAsSpecificAs(Level.WARN)) {
            sbuf.append("<font color=\"#993300\"><strong>");
            sbuf.append(Transform.escapeTags(String.valueOf(event.getLevel())));
            sbuf.append("</strong></font>");
        } else {
            sbuf.append(Transform.escapeTags(String.valueOf(event.getLevel())));
        }
        sbuf.append("</td>").append(LINE_SEP);

        String escapedLogger = Transform.escapeTags(event.getLoggerName());
        if (escapedLogger.length() == 0) {
            escapedLogger = "root";
        }
        sbuf.append("<td title=\"").append(escapedLogger).append(" category\">");
        sbuf.append(escapedLogger);
        sbuf.append("</td>").append(LINE_SEP);

        if (locationInfo) {
            StackTraceElement element = event.getSource();
            sbuf.append("<td>");
            sbuf.append(Transform.escapeTags(element.getFileName()));
            sbuf.append(':');
            sbuf.append(element.getLineNumber());
            sbuf.append("</td>").append(LINE_SEP);
        }

        sbuf.append("<td title=\"Message\">");
        sbuf.append(Transform.escapeTags(event.getMessage().getFormattedMessage()));
        sbuf.append("</td>").append(LINE_SEP);
        sbuf.append("</tr>").append(LINE_SEP);

        if (event.getContextStack().size() > 0) {
            sbuf.append(
                "<tr><td bgcolor=\"#EEEEEE\" style=\"font-size : xx-small;\" colspan=\"6\" title=\"Nested Diagnostic Context\">");
            sbuf.append("NDC: ").append(Transform.escapeTags(event.getContextStack().toString()));
            sbuf.append("</td></tr>").append(LINE_SEP);
        }


        if (event.getContextMap().size() > 0) {
            sbuf.append(
                "<tr><td bgcolor=\"#EEEEEE\" style=\"font-size : xx-small;\" colspan=\"6\" title=\"Mapped Diagnostic Context\">");
            sbuf.append("MDC: ").append(Transform.escapeTags(event.getContextMap().toString()));
            sbuf.append("</td></tr>").append(LINE_SEP);
        }

        Throwable throwable = event.getThrown();
        if (throwable != null) {
            sbuf.append("<tr><td bgcolor=\"#993300\" style=\"color:White; font-size : xx-small;\" colspan=\"6\">");
            appendThrowableAsHTML(throwable, sbuf);
            sbuf.append("</td></tr>").append(LINE_SEP);
        }

        return sbuf.toString().getBytes(charset);
    }

    void appendThrowableAsHTML(Throwable throwable, StringBuilder sbuf) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            throwable.printStackTrace(pw);
        } catch(RuntimeException ex) {
        }
        pw.flush();
        LineNumberReader reader = new LineNumberReader(new StringReader(sw.toString()));
        ArrayList<String> lines = new ArrayList<String>();
        try {
          String line = reader.readLine();
          while(line != null) {
            lines.add(line);
            line = reader.readLine();
          }
        } catch(IOException ex) {
            if (ex instanceof InterruptedIOException) {
                Thread.currentThread().interrupt();
            }
            lines.add(ex.toString());
        }
        boolean first = true;
        for (String line : lines) {
            if (!first) {
                sbuf.append(TRACE_PREFIX);
            } else {
                first = false;
            }
            sbuf.append(Transform.escapeTags(line));
            sbuf.append(LINE_SEP);
        }
    }

    /**
     * Returns appropriate HTML headers.
     */
    @Override
    public byte[] getHeader() {
        StringBuilder sbuf = new StringBuilder();
        sbuf.append(
            "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">");
        sbuf.append(LINE_SEP);
        sbuf.append("<html>").append(LINE_SEP);
        sbuf.append("<head>").append(LINE_SEP);
        sbuf.append("<title>").append(title).append("</title>").append(LINE_SEP);
        sbuf.append("<style type=\"text/css\">").append(LINE_SEP);
        sbuf.append("<!--").append(LINE_SEP);
        sbuf.append("body, table {font-family: arial,sans-serif; font-size: x-small;}").append(LINE_SEP);
        sbuf.append("th {background: #336699; color: #FFFFFF; text-align: left;}").append(LINE_SEP);
        sbuf.append("-->").append(LINE_SEP);
        sbuf.append("</style>").append(LINE_SEP);
        sbuf.append("</head>").append(LINE_SEP);
        sbuf.append("<body bgcolor=\"#FFFFFF\" topmargin=\"6\" leftmargin=\"6\">").append(LINE_SEP);
        sbuf.append("<hr size=\"1\" noshade>").append(LINE_SEP);
        sbuf.append("Log session start time " + new java.util.Date() + "<br>").append(LINE_SEP);
        sbuf.append("<br>").append(LINE_SEP);
        sbuf.append(
            "<table cellspacing=\"0\" cellpadding=\"4\" border=\"1\" bordercolor=\"#224466\" width=\"100%\">");
        sbuf.append(LINE_SEP);
        sbuf.append("<tr>").append(LINE_SEP);
        sbuf.append("<th>Time</th>").append(LINE_SEP);
        sbuf.append("<th>Thread</th>").append(LINE_SEP);
        sbuf.append("<th>Level</th>").append(LINE_SEP);
        sbuf.append("<th>Logger</th>").append(LINE_SEP);
        if (locationInfo) {
            sbuf.append("<th>File:Line</th>").append(LINE_SEP);
        }
        sbuf.append("<th>Message</th>").append(LINE_SEP);
        sbuf.append("</tr>").append(LINE_SEP);
        return sbuf.toString().getBytes(charset);
    }

    /**
     * Returns the appropriate HTML footers.
     */
    @Override
    public byte[] getFooter() {
        StringBuilder sbuf = new StringBuilder();
        sbuf.append("</table>").append(LINE_SEP);
        sbuf.append("<br>").append(LINE_SEP);
        sbuf.append("</body></html>");
        return sbuf.toString().getBytes(charset);
    }

    @PluginFactory
    public static HTMLLayout createLayout(@PluginAttr("locationInfo") String locationInfo,
                                          @PluginAttr("title") String title,
                                          @PluginAttr("contentType") String contentType,
                                          @PluginAttr("charset") String charset) {
        Charset c = Charset.isSupported("UTF-8") ? Charset.forName("UTF-8") : Charset.defaultCharset();
        if (charset != null) {
            if (Charset.isSupported(charset)) {
                c = Charset.forName(charset);
            } else {
                logger.error("Charset " + charset + " is not supported for layout, using " + c.displayName());
            }
        }
        boolean info = locationInfo == null ? false : Boolean.valueOf(locationInfo);
        if (title == null) {
            title = DEFAULT_TITLE;
        }
        if (contentType == null) {
            contentType = DEFAULT_CONTENT_TYPE;
        }
        return new HTMLLayout(info, title, contentType, c);
    }
}
