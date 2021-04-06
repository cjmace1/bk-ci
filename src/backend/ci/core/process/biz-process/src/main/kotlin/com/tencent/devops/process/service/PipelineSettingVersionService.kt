/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.process.service

import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.model.process.tables.TPipelineSettingVersion
import com.tencent.devops.process.api.service.ServicePipelineResource
import com.tencent.devops.process.dao.PipelineSettingVersionDao
import com.tencent.devops.process.pojo.pipeline.PipelineSubscriptionType
import com.tencent.devops.process.pojo.setting.PipelineRunLockType
import com.tencent.devops.process.pojo.setting.PipelineRunLockType.MULTIPLE
import com.tencent.devops.process.pojo.setting.PipelineSetting
import com.tencent.devops.process.pojo.setting.Subscription
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PipelineSettingVersionService @Autowired constructor(
    private val dslContext: DSLContext,
    private val client: Client,
    private val pipelineSettingVersionDao: PipelineSettingVersionDao
) {

    fun userGetSettingVersion(
        userId: String,
        projectId: String,
        pipelineId: String,
        version: Int,
        channelCode: ChannelCode = ChannelCode.BS
    ): PipelineSetting {
        val setting = pipelineSettingVersionDao.getSetting(dslContext, pipelineId, version)
        val labels = ArrayList<String>()
        return if (setting != null) {
            setting.map {
                with(TPipelineSettingVersion.T_PIPELINE_SETTING_VERSION) {
                    val successType = it.get(SUCCESS_TYPE).split(",").filter { i -> i.isNotBlank() }
                        .map { type -> PipelineSubscriptionType.valueOf(type) }.toSet()
                    val failType = it.get(FAIL_TYPE).split(",").filter { i -> i.isNotBlank() }
                        .map { type -> PipelineSubscriptionType.valueOf(type) }.toSet()
                    PipelineSetting(
                        projectId = projectId,
                        pipelineId = pipelineId,
                        pipelineName = it.get(NAME),
                        desc = it.get(DESC),
                        runLockType = PipelineRunLockType.valueOf(it.get(RUN_LOCK_TYPE)),
                        successSubscription = Subscription(
                            types = successType,
                            groups = it.get(SUCCESS_GROUP).split(",").toSet(),
                            users = it.get(SUCCESS_RECEIVER),
                            wechatGroupFlag = it.get(SUCCESS_WECHAT_GROUP_FLAG),
                            wechatGroup = it.get(SUCCESS_WECHAT_GROUP),
                            wechatGroupMarkdownFlag = it.get(SUCCESS_WECHAT_GROUP_MARKDOWN_FLAG),
                            detailFlag = it.get(SUCCESS_DETAIL_FLAG),
                            content = it.get(SUCCESS_CONTENT) ?: ""
                        ),
                        failSubscription = Subscription(
                            types = failType,
                            groups = it.get(FAIL_GROUP).split(",").toSet(),
                            users = it.get(FAIL_RECEIVER),
                            wechatGroupFlag = it.get(FAIL_WECHAT_GROUP_FLAG),
                            wechatGroup = it.get(FAIL_WECHAT_GROUP),
                            wechatGroupMarkdownFlag = it.get(FAIL_WECHAT_GROUP_MARKDOWN_FLAG),
                            detailFlag = it.get(FAIL_DETAIL_FLAG),
                            content = it.get(FAIL_CONTENT) ?: ""
                        ),
                        labels = labels,
                        waitQueueTimeMinute = DateTimeUtil.secondToMinute(it.get(WAIT_QUEUE_TIME_SECOND)),
                        maxQueueSize = it.get(MAX_QUEUE_SIZE),
                        version = it.get(VERSION)
                    )
                }
            }
        } else { // 此类为异常情况，正常不应该缺失数据
            val model = client.get(ServicePipelineResource::class).get(
                userId = userId,
                projectId = projectId,
                pipelineId = pipelineId,
                channelCode = channelCode).data
            val name = model?.name ?: "unknown pipeline name"
            val desc = model?.desc ?: ""
            PipelineSetting(
                projectId = projectId,
                pipelineId = pipelineId,
                pipelineName = name,
                desc = desc,
                runLockType = MULTIPLE,
                successSubscription = Subscription(),
                failSubscription = Subscription(),
                labels = labels
            )
        }
    }
}