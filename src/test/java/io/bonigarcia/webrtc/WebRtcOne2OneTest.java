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

import java.util.Arrays;
import java.util.Collection;
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

  private static final int PLAYTIME_SEC = 10;

  @Parameters(name = "{index}: {0}")
  public static Collection<Object[]> data() {
    TestScenario test = new TestScenario();
    test.addBrowser(BrowserConfig.PRESENTER, new Browser.Builder().webPageType(WebPageType.WEBRTC)
        .browserType(BrowserType.CHROME).scope(BrowserScope.LOCAL).build());
    test.addBrowser(BrowserConfig.VIEWER, new Browser.Builder().webPageType(WebPageType.WEBRTC)
        .browserType(BrowserType.CHROME).scope(BrowserScope.LOCAL).build());
    return Arrays.asList(new Object[][] { { test } });
  }

  @Test
  public void tesOne2One() throws Exception {
    // Media pipeline
    MediaPipeline mp = kurentoClient.createMediaPipeline();
    WebRtcEndpoint presenterWebRtcEp = new WebRtcEndpoint.Builder(mp).build();
    WebRtcEndpoint viewerWebRtcEP = new WebRtcEndpoint.Builder(mp).build();
    presenterWebRtcEp.connect(viewerWebRtcEP);

    // Sync presenter and viewer time
    WebRtcTestPage[] browsers = { getPresenter(), getViewer() };
    String[] videoTags = { VideoTagType.LOCAL.getId(), VideoTagType.REMOTE.getId() };
    String[] peerConnections = { "webRtcPeer.peerConnection", "webRtcPeer.peerConnection" };
    syncTimeForOcr(browsers, videoTags, peerConnections);

    // Subscribe to playing event (presenter/viewer)
    getPresenter().subscribeLocalEvents("playing");
    getPresenter().initWebRtc(presenterWebRtcEp, WebRtcChannel.AUDIO_AND_VIDEO,
        WebRtcMode.SEND_ONLY);
    getViewer().subscribeEvents("playing");
    getViewer().initWebRtc(viewerWebRtcEP, WebRtcChannel.AUDIO_AND_VIDEO, WebRtcMode.RCV_ONLY);
    getPresenter().waitForEvent("playing");
    getViewer().waitForEvent("playing");

    // Start recordings
    getPresenter().startRecording("webRtcPeer.peerConnection.getLocalStreams()[0]");
    getViewer().startRecording("webRtcPeer.peerConnection.getRemoteStreams()[0]");

    // Start OCR
    getPresenter().startOcr();
    getViewer().startOcr();

    // Play video
    waitSeconds(PLAYTIME_SEC);

    // Get OCR results and statistics
    Map<String, Map<String, String>> presenterMap = getPresenter().getOcrMap();
    Map<String, Map<String, String>> viewerMap = getViewer().getOcrMap();

    // Stop recordings
    getPresenter().stopRecording();
    getViewer().stopRecording();

    // Store recordings
    getPresenter().getRecording("presenter.webm");
    getViewer().getRecording("viewer.webm");

    // Serialize data
    serializeObject(presenterMap, "presenter.ser");
    serializeObject(viewerMap, "viewer.ser");

    // Finish OCR, close browser, release media pipeline
    getPresenter().endOcr();
    getViewer().endOcr();
    getPresenter().close();
    getViewer().close();
    mp.release();

    // Process data and write CSV
    processDataToCsv(this.getClass().getSimpleName() + ".csv", presenterMap, viewerMap);
  }

}
