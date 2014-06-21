/* 
 * This file is part of the PDF Split And Merge source code
 * Created on 14/dic/2011
 * Copyright 2011 by Andrea Vacondio (andrea.vacondio@gmail.com).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as 
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.pdfsam.ui.log;

import static org.sejda.eventstudio.StaticStudio.eventStudio;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;

import javafx.application.Platform;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.io.output.WriterOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.pdfsam.context.DefaultI18nContext;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;

/**
 * A Logback appender that dispatch the log event as a {@link LogMessageEvent} after the message has been formatted.
 * 
 * @author Andrea Vacondio
 * 
 */
@Named
public class TextAreaAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_STACK_DEPTH = 30;

    @Inject
    private PatternLayoutEncoder encoder;
    @Inject
    public LogListView logListView;

    @PostConstruct
    public void init() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        encoder.setContext(loggerContext);
        encoder.start();
        start();
        Logger logbackLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        logbackLogger.addAppender(this);
    }

    @Override
    public void append(ILoggingEvent event) {
        StringWriter writer = new StringWriter();
        try {
            encoder.init(new WriterOutputStream(writer));
            encoder.doEncode(event);
            doAppendMessage(writer.toString(), event);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doAppendMessage(String message, ILoggingEvent event) {
        if (StringUtils.isNotBlank(message)) {
            Platform.runLater(() -> logListView.appendLog(LogLevel.toLogLevel(event.getLevel().toInt()), message));
            appendStackIfAvailable(event);
            if (event.getLevel().isGreaterOrEqual(Level.ERROR)) {
                Platform.runLater(() -> eventStudio().broadcast(new ErrorLoggedEvent()));
            }
        }
    }

    private void appendStackIfAvailable(ILoggingEvent event) {
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            Platform.runLater(() -> logListView.appendLog(LogLevel.toLogLevel(event.getLevel().toInt()),
                    String.format("%s: %s", throwable.getClassName(), throwable.getMessage())));
            Arrays.stream(throwable.getStackTraceElementProxyArray())
                    .limit(MAX_STACK_DEPTH)
                    .forEach(
                            i -> Platform.runLater(() -> logListView.appendLog(
                                    LogLevel.toLogLevel(event.getLevel().toInt()), i.toString())));
            int left = throwable.getStackTraceElementProxyArray().length - MAX_STACK_DEPTH;
            if (left > 0) {
                Platform.runLater(() -> logListView.appendLog(LogLevel.toLogLevel(event.getLevel().toInt()),
                        DefaultI18nContext.getInstance().i18n("...and other {0}.", Integer.toString(left))));
            }
        }
    }

    public PatternLayoutEncoder getEncoder() {
        return encoder;
    }

    public void setEncoder(PatternLayoutEncoder encoder) {
        this.encoder = encoder;
    }

}