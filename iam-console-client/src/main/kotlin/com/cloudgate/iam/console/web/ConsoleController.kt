package com.cloudgate.iam.console.web

import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.time.Instant

/**
 * OAuth2 로그인 후 사용자 프로필과 ID Token 클레임을 뷰에 노출하는 컨트롤러
 */
@Controller
class ConsoleController(
    private val authorizedClientService: OAuth2AuthorizedClientService
) {

    /**
     * 루트 페이지에서 로그인 상태에 따라 프로필·클레임을 렌더링
     */
    @GetMapping("/")
    fun home(
        model: Model,
        authentication: Authentication?,
        @AuthenticationPrincipal oidcUser: OidcUser?,
        @RegisteredOAuth2AuthorizedClient("minicloud") authorizedClient: OAuth2AuthorizedClient?
    ): String {
        // 기본값 세팅 (로그인 안 했을 때도 뷰가 깨지지 않게)
        model.addAttribute("profile", null)
        model.addAttribute("claims", emptyMap<String, Any>())
        model.addAttribute("token", null)

        // 로그인 상태면 정보 채움
        if (authentication != null && oidcUser != null) {
            val client = authorizedClient ?: loadAuthorizedClient(authentication)

            val claims = oidcUser.idToken.claims
            model.addAttribute("profile", buildProfile(claims, oidcUser))
            model.addAttribute("claims", claims)
            model.addAttribute("token", buildTokenView(client))
        }

        return "index"
    }

    /**
     * 현재 세션 정보를 반환
     */
    @GetMapping("/api/session")
    @ResponseBody
    fun session(
        authentication: Authentication?,
        @AuthenticationPrincipal oidcUser: OidcUser?,
        @RegisteredOAuth2AuthorizedClient("minicloud") authorizedClient: OAuth2AuthorizedClient?
    ): SessionPayload {
        if (authentication == null || oidcUser == null) {
            return SessionPayload(authenticated = false)
        }

        val client = authorizedClient ?: loadAuthorizedClient(authentication)

        return SessionPayload(
            authenticated = true,
            profile = buildProfile(oidcUser.idToken.claims, oidcUser),
            claims = oidcUser.idToken.claims,
            token = buildTokenView(client)
        )
    }

    /**
     * ID Token 클레임과 OIDC 표준 필드 기반의 요약 프로필을 구성
     */
    private fun buildProfile(claims: Map<String, Any>, oidcUser: OidcUser): ProfileView =
        ProfileView(
            username = claims["preferred_username"] as? String ?: oidcUser.preferredUsername ?: oidcUser.name,
            tenantId = (claims["tenantId"] as? Number)?.toLong(),
            tenantCode = claims["tenantCode"] as? String,
            roles = (claims["roles"] as? Collection<*>)?.mapNotNull { it?.toString() }.orEmpty(),
            email = claims["email"] as? String,
            department = claims["attributes"]?.let { it as? Map<*, *> }?.get("department") as? String,
            mfaEnabled = claims["attributes"]?.let { it as? Map<*, *> }?.get("mfaEnabled") as? Boolean ?: false
        )

    /**
     * 액세스 토큰의 만료 정보와 미노출(preview) 토큰 값을 반환
     * - 토큰 전문을 노출하지 않아 보안 사고 가능성을 최소화
     */
    private fun buildTokenView(authorizedClient: OAuth2AuthorizedClient?): TokenView? {
        val client = authorizedClient ?: return null
        val tokenValue = client.accessToken.tokenValue
        val preview = if (tokenValue.length > 20) tokenValue.take(20) + "..." else tokenValue // 앞 20자만 preview 로 보여주고 나머지는 숨김

        return TokenView(
            accessTokenExpiresAt = client.accessToken.expiresAt,
            refreshTokenExpiresAt = client.refreshToken?.expiresAt,
            scopes = client.accessToken.scopes.toList().sorted(),
            accessTokenPreview = preview
        )
    }

    /**
     * 세션에 저장된 Authorized Client가 누락된 경우 서비스 기반으로 복구
     * - principalName(보통 username/sub)으로 저장소에서 다시 조회
     */
    private fun loadAuthorizedClient(authentication: Authentication?): OAuth2AuthorizedClient? {
        val principalName = authentication?.name ?: return null
        return authorizedClientService.loadAuthorizedClient("minicloud", principalName)
    }
}

data class ProfileView(
    val username: String,
    val tenantId: Long?,
    val tenantCode: String?,
    val roles: List<String>,
    val email: String?,
    val department: String?,
    val mfaEnabled: Boolean
)

data class TokenView(
    val accessTokenExpiresAt: Instant?,
    val refreshTokenExpiresAt: Instant?,
    val scopes: List<String>,
    val accessTokenPreview: String
)

data class SessionPayload(
    val authenticated: Boolean,
    val profile: ProfileView? = null,
    val claims: Map<String, Any>? = null,
    val token: TokenView? = null
)
