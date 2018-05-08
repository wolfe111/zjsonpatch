/*
 * Copyright 2016 flipkart.com zjsonpatch.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flipkart.zjsonpatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * User: gopi.vishwakarma
 * Date: 31/07/14
 */
public final class JsonPatch {

    private JsonPatch() {
    }

    private static JsonNode getPatchAttr(JsonNode jsonNode, String attr) {
        JsonNode child = jsonNode.get(attr);
        if (child == null)
            throw new InvalidJsonPatchException("Invalid JSON Patch payload (missing '" + attr + "' field)");
        return child;
    }

    private static JsonNode getPatchAttrWithDefault(JsonNode jsonNode, String attr, JsonNode defaultValue) {
        JsonNode child = jsonNode.get(attr);
        if (child == null)
            return defaultValue;
        else
            return child;
    }

    private static void process(JsonNode patch, JsonPatchProcessor processor, EnumSet<CompatibilityFlags> flags)
            throws InvalidJsonPatchException {

        if (!patch.isArray())
            throw new InvalidJsonPatchException("Invalid JSON Patch payload (not an array)");
        for (JsonNode jsonNode : patch) {
            if (!jsonNode.isObject()) throw new InvalidJsonPatchException("Invalid JSON Patch payload (not an object)");
            Operation operation = Operation.fromRfcName(getPatchAttr(jsonNode, Constants.OP).toString().replaceAll("\"", ""));
            List<String> path = PathUtils.getPath(getPatchAttr(jsonNode, Constants.PATH));

            processOperation(processor, flags, jsonNode, operation, path);
        }
    }
    private static void process(JsonNode patch, JsonPatchProcessor processor, EnumSet<CompatibilityFlags> flags, List<Operation> operationsToIgnore)
            throws InvalidJsonPatchException {

        if (!patch.isArray())
            throw new InvalidJsonPatchException("Invalid JSON Patch payload (not an array)");

        for (JsonNode jsonNode : patch) {
            if (!jsonNode.isObject()) throw new InvalidJsonPatchException("Invalid JSON Patch payload (not an object)");
            Operation operation = Operation.fromRfcName(getPatchAttr(jsonNode, Constants.OP).toString().replaceAll("\"", ""));
            List<String> path = PathUtils.getPath(getPatchAttr(jsonNode, Constants.PATH));

            if (!operationsToIgnore.contains(operation)) {
                processOperation(processor, flags, jsonNode, operation, path);
            }
        }
    }

    private static void processOperation(JsonPatchProcessor processor, EnumSet<CompatibilityFlags> flags, JsonNode jsonNode, Operation operation, List<String> path) {
        switch (operation) {
            case REMOVE: {
                processor.remove(path);
                break;
            }

            case ADD: {
                JsonNode value;
                if (!flags.contains(CompatibilityFlags.MISSING_VALUES_AS_NULLS))
                    value = getPatchAttr(jsonNode, Constants.VALUE);
                else
                    value = getPatchAttrWithDefault(jsonNode, Constants.VALUE, NullNode.getInstance());
                processor.add(path, value.deepCopy());
                break;
            }

            case REPLACE: {
                JsonNode value;
                if (!flags.contains(CompatibilityFlags.MISSING_VALUES_AS_NULLS))
                    value = getPatchAttr(jsonNode, Constants.VALUE);
                else
                    value = getPatchAttrWithDefault(jsonNode, Constants.VALUE, NullNode.getInstance());
                processor.replace(path, value.deepCopy());
                break;
            }

            case MOVE: {
                List<String> fromPath = PathUtils.getPath(getPatchAttr(jsonNode, Constants.FROM));
                processor.move(fromPath, path);
                break;
            }

            case COPY: {
                List<String> fromPath = PathUtils.getPath(getPatchAttr(jsonNode, Constants.FROM));
                processor.copy(fromPath, path);
                break;
            }

            case TEST: {
                JsonNode value;
                if (!flags.contains(CompatibilityFlags.MISSING_VALUES_AS_NULLS))
                    value = getPatchAttr(jsonNode, Constants.VALUE);
                else
                    value = getPatchAttrWithDefault(jsonNode, Constants.VALUE, NullNode.getInstance());
                processor.test(path, value.deepCopy());
                break;
            }
        }
    }


    public static void validate(JsonNode patch, EnumSet<CompatibilityFlags> flags) throws InvalidJsonPatchException {
        process(patch, NoopProcessor.INSTANCE, flags);
    }

    public static void validate(JsonNode patch) throws InvalidJsonPatchException {
        validate(patch, CompatibilityFlags.defaults());
    }

    public static JsonNode apply(JsonNode patch, JsonNode source, EnumSet<CompatibilityFlags> flags) throws JsonPatchApplicationException {
        CopyingApplyProcessor processor = new CopyingApplyProcessor(source, flags);
        process(patch, processor, flags);
        return processor.result();
    }

    public static JsonNode apply(JsonNode patch, JsonNode source) throws JsonPatchApplicationException {
        return apply(patch, source, CompatibilityFlags.defaults());
    }

    public static JsonNode applySkipOperations(JsonNode patch, JsonNode source, List<Operation> operationsToIgnore) throws JsonPatchApplicationException {
        return applySkipOperations(patch, source, CompatibilityFlags.defaults(), operationsToIgnore);
    }

    public static JsonNode applySkipOperations(JsonNode patch, JsonNode source, EnumSet<CompatibilityFlags> flags, List<Operation> operationsToIgnore) throws JsonPatchApplicationException {
        CopyingApplyProcessor processor = new CopyingApplyProcessor(source, flags);
        process(patch, processor, flags, operationsToIgnore);
        return processor.result();
    }

    public static void applyInPlace(JsonNode patch, JsonNode source) {
        applyInPlace(patch, source, CompatibilityFlags.defaults());
    }

    public static void applyInPlace(JsonNode patch, JsonNode source, EnumSet<CompatibilityFlags> flags) {
        InPlaceApplyProcessor processor = new InPlaceApplyProcessor(source, flags);
        process(patch, processor, flags);
    }
}
