package com.datorama.timbermill.unit;

import com.datorama.timbermill.common.ZonedDateTimeJacksonDeserializer;
import com.datorama.timbermill.common.ZonedDateTimeJacksonSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
		@JsonSubTypes.Type(value = StartEvent.class, name = "StartEvent"),
		@JsonSubTypes.Type(value = InfoEvent.class, name = "InfoEvent"),
		@JsonSubTypes.Type(value = SuccessEvent.class, name = "SuccessEvent"),
		@JsonSubTypes.Type(value = ErrorEvent.class, name = "ErrorEvent"),
		@JsonSubTypes.Type(value = SpotEvent.class, name = "SpotEvent")
}
)
public abstract class Event{

	private static final String DELIMITER = "___";
	private static final int MAX_CHARS = 1000000; //TODO move to configuration

	@JsonDeserialize(using = ZonedDateTimeJacksonDeserializer.class)
	@JsonSerialize(using = ZonedDateTimeJacksonSerializer.class)
	protected ZonedDateTime time;

	String primaryId;
	protected String taskId;
	protected String name;
	private String parentId;
	private Map<String, String> strings;
	private Map<String, String> texts;
	private Map<String, String> context;
	private Map<String, Number> metrics;
	private List<String> logs;
	private List<String> parentsPath;
	private String env;

	public Event() {
	}

    Event(String taskId, String name, @NotNull LogParams logParams, String parentId) {
		if (taskId == null) {
			taskId = generateTaskId(name);
		}
		this.taskId = taskId;
		this.parentId = parentId;
		this.time = ZonedDateTime.now();
		this.name = name;
		this.strings = logParams.getStrings();
		this.texts = logParams.getTexts();
		this.context = logParams.getContext();
		this.metrics = logParams.getMetrics();
		this.logs = logParams.getLogs();
	}

    public String getTaskId() {
        return taskId;
    }

	void setTaskId(String taskId) {
		this.taskId = taskId;
	}

    public String getName() {
        return name;
    }

	public void setName(String name) {
		this.name = name;
	}

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getPrimaryId() {
        return primaryId;
    }

    public void setPrimaryId(String primaryId) {
        this.primaryId = primaryId;
    }

	public Map<String, String> getStrings() {
        return strings;
    }

	public void setStrings(Map<String, String> strings) {
		this.strings = strings;
	}

	public Map<String, Number> getMetrics() {
		return metrics;
	}

	public void setMetrics(Map<String, Number> metrics) {
		this.metrics = metrics;
	}

	public Map<String, String> getTexts() {
		return texts;
	}

	public void setTexts(Map<String, String> texts) {
		this.texts = texts;
	}

	public Map<String, String> getContext() {
		return context;
	}

	public void setContext(Map<String, String> context) {
		this.context = context;
	}

    public List<String> getLogs() {
        return logs;
    }

	public void setLogs(List<String> logs) {
		this.logs = logs;
	}

	public List<String> getParentsPath() {
		return parentsPath;
	}

	public void setParentsPath(List<String> parentsPath) {
		this.parentsPath = parentsPath;
	}

	public ZonedDateTime getTime() {
		return time;
	}

	public void setTime(ZonedDateTime time) {
		this.time = time;
	}

	@JsonIgnore
	public ZonedDateTime getEndTime() {
		return null;
	}

    @Override
    public String toString() {
        return "Event{" +
                "id='" + taskId + '\'' +
                '}';
    }

    @JsonIgnore
	public abstract Task.TaskStatus getStatusFromExistingStatus(Task.TaskStatus status);

	@JsonIgnore
    public boolean isStartEvent(){
		return false;
	}

	@JsonIgnore
	public String getNameFromId() {
		if (name == null){
			String[] split = taskId.split(DELIMITER);
			if (split.length == 0){
				return null;
			}
			else {
				return split[0];
			}
		}
		else{
			return name;
		}
	}

	public static String generateTaskId(String name) {
		String uuid = UUID.randomUUID().toString();
		uuid = uuid.replace("-", "_");
		return name + DELIMITER + uuid;
	}

	public void setTrimmedStrings(@NotNull Map<String, String> strings) {
		for (Entry<String, String> entry : strings.entrySet()){
			String trimmedString = getTrimmedString(entry.getValue());
			this.strings.put(entry.getKey(), trimmedString); // Preventing ultra big strings
		}
	}

	public void setTrimmedContext(@NotNull Map<String, String> context) {
		for (Entry<String, String> entry : context.entrySet()){
			String trimmedString = getTrimmedString(entry.getValue());
			this.context.put(entry.getKey(), trimmedString); // Preventing ultra big strings
		}
	}

	public void setTrimmedTexts(@NotNull Map<String, String> text) {
		for (Entry<String, String> entry : text.entrySet()){
			String trimmedString = getTrimmedString(entry.getValue());
			this.texts.put(entry.getKey(), trimmedString); // Preventing ultra big strings
		}
	}

	private String getTrimmedString(String string) {
		int stringLength = Math.min(string.length(), MAX_CHARS);
		return string.substring(0, stringLength);
	}

	public String getEnv() {
		return env;
	}

	public void setEnv(String env) {
		this.env = env;
	}
}
