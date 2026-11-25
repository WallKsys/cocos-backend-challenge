package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "cocos.internal-tools.enabled", havingValue = "true")
public class InternalOrderToolsController {
}
