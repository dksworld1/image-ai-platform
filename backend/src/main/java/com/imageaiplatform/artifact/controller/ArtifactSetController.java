package com.imageaiplatform.artifact.controller;

import com.imageaiplatform.artifact.service.ArtifactService;
import com.imageaiplatform.artifact.domain.ArtifactSet;
import com.imageaiplatform.artifact.domain.ArtifactSetVersion;
import com.imageaiplatform.artifact.domain.ArtifactSetMember;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artifact-sets")
public class ArtifactSetController {
    private final ArtifactService service;
    public ArtifactSetController(ArtifactService service) { this.service = service; }
    public record CreateRequest(String workspaceId, String name, String purpose, String description) {}
    public record MembersRequest(List<ArtifactService.MemberInput> members) {}
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArtifactSet create(@RequestBody CreateRequest request) { return service.createSet(request.workspaceId(), request.name(), request.purpose(), request.description()); }
    @GetMapping public List<ArtifactSet> list(@RequestParam String workspaceId) { return service.sets(workspaceId); }
    @GetMapping("/{id}") public ArtifactSet detail(@PathVariable String id) { return service.set(id); }
    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ArtifactSetVersion createVersion(@PathVariable String id) { return service.createVersion(id); }
    @GetMapping("/{id}/versions") public List<ArtifactSetVersion> versions(@PathVariable String id) { return service.versions(id); }
    @GetMapping("/{id}/versions/{versionId}") public ArtifactSetVersion version(@PathVariable String id, @PathVariable String versionId) { return service.version(id, versionId); }
    @GetMapping("/{id}/versions/{versionId}/members")
    public List<ArtifactSetMember> members(@PathVariable String id, @PathVariable String versionId) { return service.members(id, versionId); }
    @PutMapping("/{id}/versions/{versionId}/members")
    public List<ArtifactSetMember> replace(@PathVariable String id, @PathVariable String versionId, @RequestBody MembersRequest request) {
        return service.replaceMembers(id, versionId, request.members());
    }
    @PostMapping("/{id}/versions/{versionId}/confirm")
    public ArtifactSetVersion confirm(@PathVariable String id, @PathVariable String versionId) { return service.confirmVersion(id, versionId); }
}
