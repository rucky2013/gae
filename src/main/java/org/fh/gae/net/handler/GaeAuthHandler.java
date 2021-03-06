package org.fh.gae.net.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.fh.gae.net.error.ErrCode;
import org.fh.gae.net.error.GaeException;
import org.fh.gae.net.utils.NettyUtils;
import org.fh.gae.net.vo.Auth;
import org.fh.gae.net.vo.BidRequest;
import org.fh.gae.net.vo.BidResponse;
import org.fh.gae.query.index.auth.AuthIndex;
import org.fh.gae.query.index.auth.AuthInfo;
import org.fh.gae.query.index.auth.AuthStatus;
import org.fh.gae.query.index.auth.AuthTriggerCondition;
import org.fh.gae.query.vo.AdSlot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;

/**
 * 授权检查
 */
@Component
@ChannelHandler.Sharable
@Slf4j
public class GaeAuthHandler extends ChannelInboundHandlerAdapter {
    @Autowired
    private AuthIndex authIndex;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        BidRequest bidRequest = (BidRequest) msg;

        // 取出授权字段
        Auth auth = bidRequest.getAuth();
        if (null == auth) {
            throw new GaeException(ErrCode.NO_AUTH);
        }


        String tid = auth.getTid();
        String token = auth.getToken();

        if (StringUtils.isAnyEmpty(tid, token)) {
            throw new GaeException(ErrCode.NO_AUTH);
        }

        // 检查tid是否存在
        AuthInfo info = authIndex.fetch(tid);
        if (null == info) {
            throw new GaeException(ErrCode.NONE_EXIST);
        }

        // 检查是否黑
        if (AuthStatus.NORMAL != info.getStatus()) {
            throw new GaeException(ErrCode.BLOCKED);
        }

        // 检查token
        if (false == StringUtils.equals(token, info.getToken())) {
            throw new GaeException(ErrCode.INVALID_TOKEN);
        }

        // 检查请求参数
        if (StringUtils.isEmpty(bidRequest.getRequestId())) {
            throw new GaeException(ErrCode.INVALID_ARG);
        }

        // 检查请求广告位信息
        List<AdSlot> slotList = bidRequest.getSlots();
        if (CollectionUtils.isEmpty(slotList)) {
            throw new GaeException(ErrCode.INVALID_ARG);
        }

        for (AdSlot slot : slotList) {
            if (StringUtils.isEmpty(slot.getSlotId())
                    || slot.getSlotType() == null
                    || slot.getH() == null
                    || slot.getW() == null
                    || slot.getMaterialType() == null
                    || 0 == slot.getMaterialType().length) {
                throw new GaeException(ErrCode.INVALID_ARG);
            }
        }

        ctx.fireChannelRead(bidRequest);

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof GaeException) {
            GaeException gaeEx = (GaeException) cause;
            BidResponse response = new BidResponse(
                    gaeEx.code(),
                    gaeEx.getMessage()
            );

            FullHttpResponse http = NettyUtils.buildResponse(response);
            ctx.writeAndFlush(http);
        } else {
            ctx.writeAndFlush(NettyUtils.buildResponse(BidResponse.error()));
        }

        ctx.close();
    }
}
