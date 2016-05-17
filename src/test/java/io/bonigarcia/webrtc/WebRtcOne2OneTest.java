/*
 * (C) Copyright 2016 Boni Garcia (http://bonigarcia.github.io/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
package io.bonigarcia.webrtc;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.test.base.FunctionalTest;
import org.kurento.test.browser.Browser;
import org.kurento.test.browser.BrowserType;
import org.kurento.test.browser.WebPageType;
import org.kurento.test.browser.WebRtcChannel;
import org.kurento.test.browser.WebRtcMode;
import org.kurento.test.browser.WebRtcTestPage;
import org.kurento.test.config.BrowserConfig;
import org.kurento.test.config.BrowserScope;
import org.kurento.test.config.TestScenario;
import org.kurento.test.latency.VideoTagType;

public class WebRtcOne2OneTest extends FunctionalTest {

  private static final int PLAYTIME = 10; // seconds to play in WebRTC

  @Parameters(name = "{index}: {0}")
  public static Collection<Object[]> data() {
    // Test: Chrome in local (presenter and viewer)
    TestScenario test = new TestScenario();
    test.addBrowser(BrowserConfig.PRESENTER, new Browser.Builder().webPageType(WebPageType.WEBRTC)
        .browserType(BrowserType.CHROME).scope(BrowserScope.LOCAL).build());
    test.addBrowser(BrowserConfig.VIEWER, new Browser.Builder().webPageType(WebPageType.WEBRTC)
        .browserType(BrowserType.CHROME).scope(BrowserScope.LOCAL).build());
    return Arrays.asList(new Object[][] { { test } });
  }

  @Test
  public void tesOne2One() throws Exception {
    // Media Pipeline
    MediaPipeline mp = kurentoClient.createMediaPipeline();
    WebRtcEndpoint masterWebRtcEp = new WebRtcEndpoint.Builder(mp).build();

    // Viewer WebRtcEndpoint
    WebRtcEndpoint viewerWebRtcEP = new WebRtcEndpoint.Builder(mp).build();
    masterWebRtcEp.connect(viewerWebRtcEP);

    // Sync presenter and viewer time
    WebRtcTestPage[] browsers = { getPresenter(), getViewer() };
    String[] videoTags = { VideoTagType.LOCAL.getId(), VideoTagType.REMOTE.getId() };
    syncTimeForOcr(browsers, videoTags);

    // Presenter playing event
    getPresenter().subscribeLocalEvents("playing");
    getPresenter().initWebRtc(masterWebRtcEp, WebRtcChannel.AUDIO_AND_VIDEO, WebRtcMode.SEND_ONLY);

    // Viewer playing event
    getViewer().subscribeEvents("playing");
    getViewer().initWebRtc(viewerWebRtcEP, WebRtcChannel.AUDIO_AND_VIDEO, WebRtcMode.RCV_ONLY);

    // Wait for events
    getPresenter().waitForEvent("playing");
    getViewer().waitForEvent("playing");

    // Start OCR
    getPresenter().startOcr();
    getViewer().startOcr();

    // Guard time to play the video
    waitSeconds(PLAYTIME);

    // Get OCR results
    Map<String, String> presenterOcr = getPresenter().getOcr();
    Map<String, String> viewerOcr = getViewer().getOcr();

    // Finish OCR
    getPresenter().endOcr();
    getViewer().endOcr();

    // Process data
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("H:mm:ss:S");
    for (String key : presenterOcr.keySet()) {
      String matchKey = containSimilarDate(key, viewerOcr.keySet());
      if (matchKey != null) {
        Date presenterDater = simpleDateFormat.parse(ocr(presenterOcr.get(key)));
        Date viewerDater = simpleDateFormat.parse(ocr(viewerOcr.get(matchKey)));
        long latency = presenterDater.getTime() - viewerDater.getTime();
        log.info("---------------> LATENCY {}", latency);
      }
    }

    log.info("Presenter OCR {} : {}", presenterOcr.size(), presenterOcr.keySet());
    log.info("Viewer OCR {} : {}", viewerOcr.size(), viewerOcr.keySet());

    // Release Media Pipeline
    mp.release();
  }

}