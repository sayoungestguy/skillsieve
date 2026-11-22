{{- define "skillsieve.labels" -}}
app.kubernetes.io/name: {{ .component }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
app.kubernetes.io/part-of: skillsieve
app.kubernetes.io/version: {{ .tag | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version }}
{{- end }}

{{- define "skillsieve.selector" -}}
app.kubernetes.io/name: {{ .component }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}

{{- define "skillsieve.image" -}}
{{- $tag := required (printf "%s.image.tag is required (sha-<short>)" .component) .image.tag -}}
{{- if eq $tag "latest" }}{{ fail "latest tag is not allowed" }}{{ end -}}
{{ printf "%s/%s:%s" .root.Values.imageRegistry .image.repository $tag }}
{{- end }}

{{- define "skillsieve.containerSecurity" -}}
runAsNonRoot: true
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: [ALL]
seccompProfile:
  type: RuntimeDefault
{{- end }}
