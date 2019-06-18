package com.kineticdata.bridgehub.adapter.vmware;

import com.kineticdata.bridgehub.adapter.QualificationParser;

public class VMWareQualificationParser extends QualificationParser {
    public String encodeParameter(String name, String value) {
        return value;
    }
}
