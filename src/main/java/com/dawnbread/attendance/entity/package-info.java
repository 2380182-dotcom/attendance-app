/**
 * Single shared definition of the tenant-scoping filter, applied per-entity
 * via {@code @Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")}
 * on each of the 12 tenant-scoped entities. Enabled per-request by
 * SecurityInterceptor.
 */
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
package com.dawnbread.attendance.entity;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
