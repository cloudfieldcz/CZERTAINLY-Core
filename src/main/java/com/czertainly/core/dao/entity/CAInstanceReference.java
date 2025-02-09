package com.czertainly.core.dao.entity;

import com.czertainly.core.util.DtoMapper;
import com.czertainly.api.model.ca.CAInstanceDto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ca_instance_reference")
public class CAInstanceReference extends Audited implements Serializable, DtoMapper<CAInstanceDto> {
    private static final long serialVersionUID = -2377655450967447704L;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ca_instance_reference_seq")
    @SequenceGenerator(name = "ca_instance_reference_seq", sequenceName = "ca_instance_reference_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "ca_instance_id")
    private Long caInstanceId;

    @Column(name = "name")
    private String name;

    @Column(name = "status")
    private String status;

    @Column(name = "type")
    private String authorityType;

    @ManyToOne
    @JoinColumn(name = "connector_id")
    private Connector connector;

    @Column(name="connector_name")
    private String connectorName;

    @OneToMany(mappedBy = "caInstanceReference")
    @JsonIgnore
    private Set<RaProfile> raProfiles = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCaInstanceId() {
        return caInstanceId;
    }

    public void setCaInstanceId(Long caInstanceId) {
        this.caInstanceId = caInstanceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
    }

    public Set<RaProfile> getRaProfiles() {
        return raProfiles;
    }

    public void setRaProfiles(Set<RaProfile> raProfiles) {
        this.raProfiles = raProfiles;
    }

    public String getAuthorityType() {
        return authorityType;
    }

    public void setAuthorityType(String authorityType) {
        this.authorityType = authorityType;
    }

    public String getConnectorName() { return connectorName; }

    public void setConnectorName(String connectorName) { this.connectorName = connectorName; }

    public CAInstanceDto mapToDto() {
        CAInstanceDto dto = new CAInstanceDto();
        dto.setId(this.id);
        dto.setUuid(this.uuid);
        dto.setName(this.name);
        dto.setStatus(this.status);
        dto.setAuthorityType(authorityType);
        dto.setConnectorName(this.connectorName);
        if (this.connector != null) {
            dto.setConnectorUuid(this.connector.getUuid());
        }
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("uuid", uuid)
                .append("caInstanceId", caInstanceId)
                .append("name", name)
                .append("status", status)
                .append("type", authorityType)
                .append("connectorName", connectorName)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CAInstanceReference that = (CAInstanceReference) o;
        return new EqualsBuilder().append(id, that.id).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(id).toHashCode();
    }
}
