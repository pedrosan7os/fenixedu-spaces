package org.fenixedu.spaces.domain;

import java.math.BigDecimal;

import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.spaces.ui.InformationBean;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import com.google.gson.JsonObject;

public class Space extends Space_Base {

    public Space(Information information) {
        this(null, information);
    }

    public Space(Space parent, Information information) {
        setCreated(new DateTime());
        add(information);
        setParent(parent);
        if (parent == null) {
            setBennu(Bennu.getInstance());
        }
    }

    public InformationBean bean() throws UnavailableException {
        return Information.builder(getInformation()).bean();
    }

    public void bean(InformationBean informationBean) {
        add(Information.builder(informationBean).build());
    }

    public String getName() throws UnavailableException {
        return getInformation().getName();
    }

    public SpaceClassification getClassification() throws UnavailableException {
        return getInformation().getClassification();
    }

    @SuppressWarnings("unchecked")
    public <T extends Object> T getMetadata(String field) throws UnavailableException {
        Information information = getInformation();
        final MetadataSpec metadataSpec = information.getClassification().getMetadataSpec(field);
        final Class<?> type = metadataSpec.getType();
        final JsonObject metadata = information.getMetadata().getAsJsonObject();

        if (Boolean.class.isAssignableFrom(type)) {
            return (T) new Boolean(metadata.get(field).getAsBoolean());
        }
        if (Integer.class.isAssignableFrom(type)) {
            return (T) new Integer(metadata.get(field).getAsInt());
        }
        if (String.class.isAssignableFrom(type)) {
            return (T) new String(metadata.get(field).getAsString());
        }
        if (BigDecimal.class.isAssignableFrom(type)) {
            return (T) metadata.get(field).getAsBigDecimal();
        }

        throw new UnavailableException();
    }

    /**
     * get the most recent space information
     * 
     * @return
     * @throws UnavailableException
     */
    protected Information getInformation() throws UnavailableException {
        return getInformation(new DateTime());
    }

    /**
     * get the most recent space information valid at the specified datetime.
     * 
     * @param when
     * @return
     * @throws UnavailableException
     */

    protected Information getInformation(DateTime when) throws UnavailableException {
        return getInformation(when, new DateTime());
    }

    /**
     * get the space information valid at the specified when date, created on atWhatDate.
     * 
     * @param when
     * @param atWhatDate
     * @return
     */

    protected Information getInformation(final DateTime when, final DateTime creationDate) throws UnavailableException {
        Information current = getCurrent();
        while (current != null) {
            if (current.contains(when)) {
                return current;
            }
            current = current.getPrevious();
        }
        throw new UnavailableException();
    }

    protected void add(Information information) {
        if (getCurrent() == null) {
            setCurrent(information);
            return;
        }
        final DateTime newStart = information.getValidFrom();
        final DateTime newEnd = information.getValidUntil();

        final Interval newValidity = information.getValidity();

        Information newCurrent = null;
        Information last = null;
        Information newHead = null;

        Information current = getCurrent();
        Information head = current;
        Interval currentValidity = current.getValidity();

        boolean foundEnd = false;
        boolean foundStart = false;

        // insert at head
        if (newValidity.isAfter(currentValidity)) {
            newHead = information;
            newHead.setPrevious(head);
        }

        if (newHead == null) {

            //last is the previous element of the new list
            //newCurrent is the current element of the new list

            while (current != null) {
                if (!foundEnd && !foundStart && current.contains(newValidity)) { //if start and end is in the current element
                    if (current.getValidity().equals(newValidity)) { // if it is the same period just replace current
                        newCurrent = information;
                    } else {
                        Information right = current.keepRight(newEnd);
                        if (last != null) {
                            last.setPrevious(right);
                        } else {
                            newHead = right; // no previous in new list, make right head
                        }
                        right.setPrevious(information);
                        last = information;
                        newCurrent = current.keepLeft(newStart);
                    }
                    foundEnd = true;
                    foundStart = true;
                } else {
                    if (!foundEnd) {
                        final boolean isAfter = current.isAfter(newEnd); //if newEnd is after current end date, then it is a gap
                        if (current.contains(newEnd) || isAfter) {
                            if (!isAfter) {
                                Information right = current.keepRight(newEnd);
                                if (last != null) {
                                    last.setPrevious(right);
                                } else {
                                    newHead = right; // no previous in new list, make right head
                                }
                                last = right;
                            }
                            newCurrent = information; // no need to cut current because it will be replaced by information
                            foundEnd = true;
                        }
                    }
                    final boolean isAfter = current.isAfter(newStart); //if newEnd is after current end date, then it is a gap
                    if (foundEnd && (current.contains(newStart) || isAfter)) { // looking for the start
                        newCurrent = current.keepLeft(newStart);
                        foundStart = true;
                    } else {
                        if (!foundEnd || foundStart) { // if not in the process of searching for information just keep copying current
                            newCurrent = current.copy();
                        }
                    }
                }

                //bookkeeping code
                if (last != null && !last.equals(newCurrent)) {
                    last.setPrevious(newCurrent);
                }

                last = newCurrent;

                if (newHead == null) {
                    newHead = newCurrent;
                }

                current = current.getPrevious();
            }

            //insert at end
            if (!foundEnd) {
                last.setPrevious(information);
            }
        }

        addHistory(head);
        setCurrent(newHead);
    }

}