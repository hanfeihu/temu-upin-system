package com.tminos.productscene.dto;

import java.util.List;
import java.util.Map;

public class PlatformConfigDTO {

    public static class ProfileResponse {
        private Long id;
        private String name;
        private Boolean isDefault;
        private Map<String, String> items;

        public ProfileResponse() {}

        public ProfileResponse(Long id, String name, Boolean isDefault, Map<String, String> items) {
            this.id = id;
            this.name = name;
            this.isDefault = isDefault;
            this.items = items;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
        public Map<String, String> getItems() { return items; }
        public void setItems(Map<String, String> items) { this.items = items; }
    }

    public static class SaveProfileRequest {
        private String name;
        private Boolean isDefault;
        private Map<String, String> items;

        public SaveProfileRequest() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
        public Map<String, String> getItems() { return items; }
        public void setItems(Map<String, String> items) { this.items = items; }
    }

    public static class SetDefaultRequest {
        private Long profileId;
        public SetDefaultRequest() {}
        public Long getProfileId() { return profileId; }
        public void setProfileId(Long profileId) { this.profileId = profileId; }
    }

    public static class ListProfilesResponse {
        private List<ProfileResponse> profiles;
        public ListProfilesResponse() {}
        public ListProfilesResponse(List<ProfileResponse> profiles) { this.profiles = profiles; }
        public List<ProfileResponse> getProfiles() { return profiles; }
        public void setProfiles(List<ProfileResponse> profiles) { this.profiles = profiles; }
    }
}
