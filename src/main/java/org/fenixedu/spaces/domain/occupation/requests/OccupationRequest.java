/**
 * Copyright © 2014 Instituto Superior Técnico
 *
 * This file is part of FenixEdu Spaces.
 *
 * FenixEdu Spaces is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu Spaces is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu Spaces.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fenixedu.spaces.domain.occupation.requests;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.core.domain.User;
import org.fenixedu.spaces.domain.Space;
import org.fenixedu.spaces.domain.SpaceDomainException;
import org.joda.time.DateTime;

public class OccupationRequest extends OccupationRequest_Base {

    public static final Comparator<OccupationRequest> COMPARATOR_BY_IDENTIFICATION = new Comparator<OccupationRequest>() {
        @Override
        public int compare(OccupationRequest o1, OccupationRequest o2) {
            return o1.getIdentification().compareTo(o2.getIdentification());
        }
    };

    public static final Comparator<OccupationRequest> COMPARATOR_BY_INSTANT = new Comparator<OccupationRequest>() {

        @Override
        public int compare(OccupationRequest o1, OccupationRequest o2) {
            int o = o1.getInstant().compareTo(o2.getInstant());
            return o != 0 ? o : o1.getExternalId().compareTo(o2.getExternalId());
        }
    };

    public static final Comparator<OccupationRequest> COMPARATOR_BY_MORE_RECENT_COMMENT_INSTANT =
            new Comparator<OccupationRequest>() {
                @Override
                public int compare(OccupationRequest o1, OccupationRequest o2) {
                    int o = o1.getMoreRecentCommentInstant().compareTo(o2.getMoreRecentCommentInstant());
                    return o != 0 ? o : o1.getExternalId().compareTo(o2.getExternalId());
                }
            };

    public OccupationRequest(User requestor, String subject, Space campus, String description) {
        super();
        checkIfRequestAlreadyExists(requestor, subject, description);
        setRootDomainObject(Bennu.getInstance());
        setRequestor(requestor);
        DateTime now = new DateTime();
        setInstant(now);
        setCampus(campus);
        addStateInstants(new OccupationStateInstant(this, OccupationRequestState.NEW, now));
        addComment(new OccupationComment(this, subject, description, requestor, now));
        setTeacherReadComments(1);
        setEmployeeReadComments(0);
        setIdentification(getNextRequestIdentification());
    }

    @jvstm.cps.ConsistencyPredicate
    protected boolean checkRequiredParameters() {
        return getInstant() != null && getIdentification() != null;
    }

    public Integer getNumberOfNewComments(User person) {
        if (person.equals(getOwner())) {
            return getCommentSet().size() - getEmployeeReadComments();
        } else if (person.equals(getRequestor())) {
            return getCommentSet().size() - getTeacherReadComments();
        }
        return Integer.valueOf(0);
    }

    public DateTime getMoreRecentCommentInstant() {
        SortedSet<OccupationComment> result = new TreeSet<OccupationComment>(OccupationComment.COMPARATOR_BY_INSTANT);
        result.addAll(getCommentSet());
        return result.last().getInstant();
    }

    public void createNewTeacherOrEmployeeComment(String description, User commentOwner, DateTime instant) {
        new OccupationComment(this, getCommentSubject(), description, commentOwner, instant);
        if (commentOwner.equals(getRequestor())) {
            setTeacherReadComments(getCommentSet().size());
        } else {
            setOwner(commentOwner);
            setEmployeeReadComments(getCommentSet().size());
        }
    }

    public void createNewTeacherCommentAndOpenRequest(String description, User commentOwner, DateTime instant) {
        openRequestWithoutAssociateOwner(instant);
        new OccupationComment(this, getCommentSubject(), description, commentOwner, instant);
        setTeacherReadComments(getCommentSet().size());
    }

    public void createNewEmployeeCommentAndCloseRequest(String description, User commentOwner, DateTime instant) {
        new OccupationComment(this, getCommentSubject(), description, commentOwner, instant);
        closeRequestWithoutAssociateOwner(instant);
        setOwner(commentOwner);
        setEmployeeReadComments(getCommentSet().size());
    }

    public void closeRequestAndAssociateOwnerOnlyForEmployees(DateTime instant, User person) {
        closeRequestWithoutAssociateOwner(instant);
        if (!getOwner().equals(person)) {
            setEmployeeReadComments(0);
            setOwner(person);
        }
    }

    public void openRequestAndAssociateOwnerOnlyForEmployess(DateTime instant, User person) {
        openRequestWithoutAssociateOwner(instant);
        if (getOwner() == null || !getOwner().equals(person)) {
            setEmployeeReadComments(0);
            setOwner(person);
        }
    }

    private void closeRequestWithoutAssociateOwner(DateTime instant) {
        if (!getCurrentState().equals(OccupationRequestState.RESOLVED)) {
            addStateInstants(new OccupationStateInstant(this, OccupationRequestState.RESOLVED, instant));
        }
    }

    private void openRequestWithoutAssociateOwner(DateTime instant) {
        if (!getCurrentState().equals(OccupationRequestState.OPEN)) {
            addStateInstants(new OccupationStateInstant(this, OccupationRequestState.OPEN, instant));
        }
    }

    public String getCommentSubject() {
        StringBuilder subject = new StringBuilder();
        subject.append("Re: ");
        OccupationComment firstComment = getFirstComment();
        if (firstComment != null) {
            subject.append(firstComment.getSubject());
        }
        return subject.toString();
    }

    @Override
    public void setOwner(User owner) {
        if (owner == null || !owner.equals(getRequestor())) {
            super.setOwner(owner);
        }
    }

    @Override
    public void setIdentification(Integer identification) {
        if (identification == null) {
            throw new SpaceDomainException("error.OccupationRequest.empty.identification");
        }
        super.setIdentification(identification);
    }

    @Override
    public void setRequestor(User requestor) {
        if (requestor == null) {
            throw new SpaceDomainException("error.OccupationRequest.empty.requestor");
        }
        super.setRequestor(requestor);
    }

    @Override
    public void setInstant(DateTime instant) {
        if (instant == null) {
            throw new SpaceDomainException("error.OccupationRequest.empty.instant");
        }
        super.setInstant(instant);
    }

    public String getPresentationInstant() {
        return getInstant().toString("dd/MM/yyyy HH:mm");
    }

    public static List<OccupationRequest> getRequestsByTypeOrderByDate(OccupationRequestState state, Space campus) {

        return Bennu.getInstance().getOccupationRequestSet().stream()
                .filter(r -> r.getCurrentState().equals(state) && (r.getCampus() == null || r.getCampus().equals(campus)))
                .sorted(OccupationRequest.COMPARATOR_BY_INSTANT.reversed()).collect(Collectors.toList());

    }

    public static OccupationRequest getRequestById(Integer requestID) {
        for (OccupationRequest request : Bennu.getInstance().getOccupationRequestSet()) {
            if (request.getIdentification().equals(requestID)) {
                return request;
            }
        }
        return null;
    }

    public static Set<OccupationRequest> getResolvedRequestsOrderByMoreRecentComment(Space campus) {
        Set<OccupationRequest> result =
                new TreeSet<OccupationRequest>(OccupationRequest.COMPARATOR_BY_MORE_RECENT_COMMENT_INSTANT);
        for (OccupationRequest request : Bennu.getInstance().getOccupationRequestSet()) {
            if (request.getCurrentState().equals(OccupationRequestState.RESOLVED)
                    && (request.getCampus() == null || request.getCampus().equals(campus))) {
                result.add(request);
            }
        }
        return result;
    }

    public static Set<OccupationRequest> getRequestsByTypeAndDiferentOwnerOrderByDate(OccupationRequestState state, User owner,
            Space campus) {
        Set<OccupationRequest> result = new TreeSet<OccupationRequest>(OccupationRequest.COMPARATOR_BY_INSTANT);
        for (OccupationRequest request : Bennu.getInstance().getOccupationRequestSet()) {
            if (request.getCurrentState().equals(state) && (request.getOwner() == null || !request.getOwner().equals(owner))
                    && (request.getCampus() == null || request.getCampus().equals(campus))) {
                result.add(request);
            }
        }
        return result;
    }

    public OccupationComment getFirstComment() {
        for (OccupationComment comment : getCommentSet()) {
            if (comment.getInstant().isEqual(getInstant())) {
                return comment;
            }
        }
        return null;
    }

    public Set<OccupationComment> getCommentsWithoutFirstCommentOrderByDate() {
        Set<OccupationComment> result = new TreeSet<OccupationComment>(OccupationComment.COMPARATOR_BY_INSTANT);
        for (OccupationComment comment : getCommentSet()) {
            if (!comment.getInstant().isEqual(getInstant())) {
                result.add(comment);
            }
        }
        return result;
    }

    public String getSubject() {
        final OccupationComment firstComment = getFirstComment();
        final String content = firstComment != null ? firstComment.getSubject() : null;
        return content == null || content.isEmpty() ? getIdentification().toString() : content;
    }

    public String getDescription() {
        final OccupationComment firstComment = getFirstComment();
        final String description = firstComment == null ? null : firstComment.getDescription();
        final String content = description == null ? null : description;
        return content == null ? getExternalId() : content;
    }

    public OccupationRequestState getCurrentState() {
        SortedSet<OccupationStateInstant> result =
                new TreeSet<OccupationStateInstant>(OccupationStateInstant.COMPARATOR_BY_INSTANT);

        result.addAll(getStateInstantsSet());
        return result.last().getRequestState();
    }

    public OccupationRequestState getState(DateTime instanTime) {
        if (instanTime == null) {
            return getCurrentState();
        } else {
            for (OccupationStateInstant stateInstant : getStateInstantsSet()) {
                if (stateInstant.getInstant().isEqual(instanTime)) {
                    return stateInstant.getRequestState();
                }
            }
        }
        return null;
    }

    private Integer getNextRequestIdentification() {
        SortedSet<OccupationRequest> result = new TreeSet<OccupationRequest>(OccupationRequest.COMPARATOR_BY_IDENTIFICATION);
        Collection<OccupationRequest> requests = Bennu.getInstance().getOccupationRequestSet();
        for (OccupationRequest request : requests) {
            if (!request.equals(this)) {
                result.add(request);
            }
        }
        return result.isEmpty() ? 1 : result.last().getIdentification() + 1;
    }

    private void checkIfRequestAlreadyExists(User requestor, String subject, String description) {
        Set<OccupationRequest> requests = requestor.getOccupationRequestSet();
        for (OccupationRequest request : requests) {
            OccupationComment firstComment = request.getFirstComment();
            if (firstComment != null && firstComment.getSubject() != null && firstComment.getSubject().compareTo(subject) == 0
                    && firstComment.getDescription() != null && firstComment.getDescription().compareTo(description) == 0) {
                throw new SpaceDomainException("error.OccupationRequest.request.already.exists");
            }
        }
    }

}
