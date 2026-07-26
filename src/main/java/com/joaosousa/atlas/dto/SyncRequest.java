package com.joaosousa.atlas.dto;

import java.util.List;

public class SyncRequest {

    private List<ExerciseEntry> exercise;

    public List<ExerciseEntry> getExercise() { return exercise; }
    public void setExercise(List<ExerciseEntry> exercise) { this.exercise = exercise; }

    public static class ExerciseEntry {
        private String type;
        private String start_time;
        private int duration_seconds;
        private MetadataEntry metadata;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStart_time() { return start_time; }
        public void setStart_time(String start_time) { this.start_time = start_time; }
        public int getDuration_seconds() { return duration_seconds; }
        public void setDuration_seconds(int duration_seconds) { this.duration_seconds = duration_seconds; }
        public MetadataEntry getMetadata() { return metadata; }
        public void setMetadata(MetadataEntry metadata) { this.metadata = metadata; }
    }

    public static class MetadataEntry {
        private String data_origin;
        private String recording_method;

        public String getData_origin() { return data_origin; }
        public void setData_origin(String data_origin) { this.data_origin = data_origin; }
        public String getRecording_method() { return recording_method; }
        public void setRecording_method(String recording_method) { this.recording_method = recording_method; }
    }
}
