package org.openmrs.module.ugandaemr.web.resource;

import org.openmrs.api.context.Context;
import org.openmrs.module.patientqueueing.api.PatientQueueingService;
import org.openmrs.module.ugandaemr.web.customdto.QueueKioskEntry;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.util.OpenmrsUtil;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Resource(name = RestConstants.VERSION_1 + "/kiosk", supportedClass = QueueKioskEntry.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class QueueKioskResource extends DelegatingCrudResource<QueueKioskEntry> {
	
	private PatientQueueingService service() {
		return Context.getService(PatientQueueingService.class);
	}
	
	@Override
	public QueueKioskEntry newDelegate() {
		return new QueueKioskEntry();
	}
	
	/**
	 * Status lookup (path style): GET /ws/rest/v1/patientqueueing/kiosk/{ticketNumber}
	 */
	@Override
	public QueueKioskEntry getByUniqueId(String ticketNumber) {
		throw new ResourceDoesNotSupportOperationException("QueueKioskResource does not support getByUniqueId");
	}
	
	/**
	 * Status lookup (query style): GET /ws/rest/v1/patientqueueing/kiosk?ticketNumber=XXX OpenMRS
	 * routes query params as "search", so implement doSearch. Returns NeedsPaging<PatientQueue>
	 * (typed).
	 */
	@Override
	protected NeedsPaging<QueueKioskEntry> doSearch(RequestContext context) throws ResponseException {
		String ticketNumber = context.getParameter("visitNumber");
		if (ticketNumber == null || ticketNumber.trim().isEmpty()) {
			throw new ResourceDoesNotSupportOperationException("ticketNumber is required");
		}
		
		Date now = new Date();
		
		List<QueueKioskEntry> list = service().getPatientQueueByVisitNumber(ticketNumber, OpenmrsUtil.firstSecondOfDay(now),
		    OpenmrsUtil.getLastMomentOfDay(now)).stream().map(q -> new QueueKioskEntry(q)).collect(Collectors.toList());
		
		if (list == null || list.isEmpty()) {
			throw new ResourceDoesNotSupportOperationException("Ticket not found: " + ticketNumber);
		}
		
		return new NeedsPaging<QueueKioskEntry>(list, context);
	}
	
	/**
	 * Check-in: POST /ws/rest/v1/patientqueueing/kiosk body: { "ticketNumber": "...",
	 * "facilityUuid": "..." } header: X-Device-Id optional
	 */
	@Override
	public QueueKioskEntry create(SimpleObject propertiesToCreate, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("QueueKioskResource does not support getByUniqueId");
	}
	
	// --- Unsupported operations ---
	
	@Override
	public QueueKioskEntry save(QueueKioskEntry delegate) {
		throw new ResourceDoesNotSupportOperationException("QueueKioskResource does not support save/update");
	}
	
	@Override
	public SimpleObject update(String uuid, SimpleObject propertiesToUpdate, RequestContext context)
	        throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("QueueKioskResource does not support update");
	}
	
	@Override
	public SimpleObject getAll(RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("QueueKioskResource does not support getAll");
	}
	
	@Override
	protected void delete(QueueKioskEntry patientQueue, String reason, RequestContext requestContext) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("QueueKioskResource does not support delete");
	}
	
	@Override
	public void purge(QueueKioskEntry patientQueue, RequestContext requestContext) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("QueueKioskResource does not support purge");
	}
	
	// --- REST representations ---
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		if (rep instanceof DefaultRepresentation) {
			DelegatingResourceDescription d = new DelegatingResourceDescription();
			d.addProperty("uuid");
			d.addProperty("visitNumber");
			d.addProperty("status");
			d.addProperty("locationTo", Representation.REF); // queue location
			d.addProperty("queueRoom", Representation.REF); // service room
			d.addProperty("datePicked");
			d.addProperty("dateCompleted");
			d.addProperty("dateCancelled");
			d.addProperty("dateCreated");
			d.addSelfLink();
			return d;
		}
		
		if (rep instanceof FullRepresentation) {
			DelegatingResourceDescription d = new DelegatingResourceDescription();
			d.addProperty("uuid");
			d.addProperty("visitNumber");
			d.addProperty("status");
			d.addProperty("locationTo");
			d.addProperty("queueRoom");
			d.addProperty("datePicked");
			d.addProperty("dateCompleted");
			d.addProperty("dateCancelled");
			d.addProperty("dateCreated");
			d.addProperty("dateChanged");
			d.addProperty("creator", Representation.REF);
			d.addProperty("changedBy", Representation.REF);
			d.addSelfLink();
			d.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
			return d;
		}
		
		return null;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		return null;
	}
	
	@Override
	public DelegatingResourceDescription getUpdatableProperties() {
		return null;
	}
	
	private String asString(SimpleObject obj, String key) {
		Object v = (obj == null) ? null : obj.get(key);
		return (v == null) ? null : String.valueOf(v);
	}
}
