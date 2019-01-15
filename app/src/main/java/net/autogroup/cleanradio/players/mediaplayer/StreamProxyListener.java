package net.autogroup.cleanradio.players.mediaplayer;

import net.autogroup.cleanradio.data.ShoutcastInfo;
import net.autogroup.cleanradio.data.StreamLiveInfo;

interface StreamProxyListener {
    void onFoundShoutcastStream(ShoutcastInfo bitrate, boolean isHls);
    void onFoundLiveStreamInfo(StreamLiveInfo liveInfo);
    void onStreamCreated(String proxyConnection);
    void onStreamStopped();
    void onBytesRead(byte[] buffer, int offset, int length);
}
