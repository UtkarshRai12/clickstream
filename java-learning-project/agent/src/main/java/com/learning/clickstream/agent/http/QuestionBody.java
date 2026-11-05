package com.learning.clickstream.agent.http;

/** The follow-up POST body. An empty question is rejected before the writer runs. */
record QuestionBody(String question) {}
