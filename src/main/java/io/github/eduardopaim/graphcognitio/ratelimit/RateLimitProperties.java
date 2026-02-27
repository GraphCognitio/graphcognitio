package io.github.eduardopaim.graphcognitio.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private Rule authRegister = new Rule(5, 60);
    private Rule authLogin = new Rule(10, 60);
    private Rule createPost = new Rule(20, 60);
    private Rule createReply = new Rule(40, 60);
    private Rule likes = new Rule(120, 60);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Rule getAuthRegister() {
        return authRegister;
    }

    public void setAuthRegister(Rule authRegister) {
        this.authRegister = authRegister;
    }

    public Rule getAuthLogin() {
        return authLogin;
    }

    public void setAuthLogin(Rule authLogin) {
        this.authLogin = authLogin;
    }

    public Rule getCreatePost() {
        return createPost;
    }

    public void setCreatePost(Rule createPost) {
        this.createPost = createPost;
    }

    public Rule getCreateReply() {
        return createReply;
    }

    public void setCreateReply(Rule createReply) {
        this.createReply = createReply;
    }

    public Rule getLikes() {
        return likes;
    }

    public void setLikes(Rule likes) {
        this.likes = likes;
    }

    public static class Rule {
        private int limit;
        private int windowSeconds;

        public Rule() {
        }

        public Rule(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
