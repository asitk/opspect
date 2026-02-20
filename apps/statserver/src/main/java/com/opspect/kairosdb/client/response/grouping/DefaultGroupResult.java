package com.opspect.kairosdb.client.response.grouping;

import static com.opspect.kairosdb.client.util.Preconditions.checkNotNullOrEmpty;

import com.opspect.kairosdb.client.response.GroupResult;

/** Group that represents natural grouping based on the type of the data. */
public class DefaultGroupResult extends GroupResult {
  private String type;

  public DefaultGroupResult(String name, String type) {
    super(name);
    this.type = checkNotNullOrEmpty(type);
  }

  /**
   * Returns the type of data.
   *
   * @return type of the data
   */
  public String getType() {
    return type;
  }
}
